package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeArgumentException
import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeUserContact
import fi.hel.haitaton.hanke.domain.Perustaja
import fi.hel.haitaton.hanke.domain.UserContact
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.email.HankeInvitationData
import fi.hel.haitaton.hanke.logging.HankeKayttajaLoggingService
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeKayttajaService(
    private val hankeKayttajaRepository: HankeKayttajaRepository,
    private val kayttajaTunnisteRepository: KayttajaTunnisteRepository,
    private val hankeRepository: HankeRepository,
    private val permissionService: PermissionService,
    private val featureFlags: FeatureFlags,
    private val logService: HankeKayttajaLoggingService,
    private val emailSenderService: EmailSenderService,
) {
    @Transactional(readOnly = true)
    fun getKayttajatByHankeId(hankeId: Int): List<HankeKayttajaDto> =
        hankeKayttajaRepository.findByHankeId(hankeId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getKayttajaByUserId(hankeId: Int, userId: String): HankeKayttajaEntity? {
        val permission = permissionService.findPermission(hankeId, userId)
        if (permission == null) {
            logger.warn {
                "UserId=$userId does not have a permission instance for HankeId=$hankeId"
            }
            return null
        }

        val hankeKayttaja = hankeKayttajaRepository.findByPermissionId(permission.id)
        if (hankeKayttaja == null) {
            logger.warn { "No kayttaja instance found (hankeId=$hankeId, userId=$userId) " }
            return null
        }

        return hankeKayttaja
    }

    @Transactional
    fun saveNewTokensFromApplication(
        application: ApplicationEntity,
        hankeId: Int,
        hankeTunnus: String,
        hankeNimi: String,
        currentUserId: String,
        currentKayttaja: HankeKayttajaEntity? = null,
    ) {
        logger.info {
            "Creating users and user tokens for application ${application.id}, alluid=${application.alluid}}"
        }

        val contacts =
            application.applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { HankeUserContact.from(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact ->
            createTunnisteAndKayttaja(
                hankeId,
                hankeTunnus,
                hankeNimi,
                currentKayttaja,
                contact,
                currentUserId
            )
        }
    }

    @Transactional
    fun saveNewTokensFromHanke(hanke: Hanke, userId: String) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }
        logger.info {
            "Creating users and user tokens for hanke ${hanke.id}, hankeTunnus=${hanke.hankeTunnus}}"
        }

        val hankeId = hanke.id ?: throw HankeArgumentException("Hanke without id")
        val hankeTunnus = hanke.hankeTunnus ?: throw HankeArgumentException("Hanke without tunnus")
        val hankeNimi = hanke.nimi ?: throw HankeArgumentException("Hanke without name")

        val contacts =
            hanke
                .extractYhteystiedot()
                .flatMap { it.alikontaktit }
                .mapNotNull { HankeUserContact.from(it.fullName(), it.email) }

        val inviter = getKayttajaByUserId(hankeId, userId)
        filterNewContacts(hankeId, contacts).forEach { contact ->
            createTunnisteAndKayttaja(hankeId, hankeTunnus, hankeNimi, inviter, contact, userId)
        }
    }

    @Transactional
    fun addHankeFounder(hankeId: Int, perustaja: Perustaja?, currentUser: String) {
        val permissionEntity =
            permissionService.create(hankeId, currentUser, Kayttooikeustaso.KAIKKI_OIKEUDET)

        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }
        perustaja?.let {
            logger.info { "Saving user for Hanke perustaja." }
            createUser(
                currentUser,
                hankeId = hankeId,
                nimi = it.nimi!!,
                sahkoposti = it.email,
                permission = permissionEntity,
            )
        }
    }

    @Transactional
    fun updatePermissions(
        hankeIdentifier: HankeIdentifier,
        updates: Map<UUID, Kayttooikeustaso>,
        deleteAdminPermission: Boolean,
        userId: String,
    ) {
        logger.info { "Updating permissions for hankekayttajat. ${hankeIdentifier.logString()}" }

        val kayttajat =
            hankeKayttajaRepository.findByHankeIdAndIdIn(hankeIdentifier.id, updates.keys)

        if (kayttajat.any { it.permission?.userId == userId }) {
            throw ChangingOwnPermissionException(userId)
        }

        validateAllKayttajatFound(kayttajat, updates, hankeIdentifier)
        validateAdminPermissionIfNeeded(kayttajat, updates, deleteAdminPermission, userId)

        kayttajat.forEach { kayttaja ->
            kayttaja.permission?.let { permission ->
                permissionService.updateKayttooikeustaso(permission, updates[kayttaja.id]!!, userId)
            }
                ?: kayttaja.kayttajaTunniste?.let { tunniste ->
                    updateKayttooikeustaso(tunniste, updates[kayttaja.id]!!, userId)
                }
        }

        validateAdminRemains(hankeIdentifier)
    }

    @Transactional
    fun createPermissionFromToken(userId: String, tunniste: String): HankeKayttaja {
        logger.info { "Trying to activate token $tunniste for user $userId" }
        val tunnisteEntity =
            kayttajaTunnisteRepository.findByTunniste(tunniste)
                ?: throw TunnisteNotFoundException(userId, tunniste)

        val kayttaja = tunnisteEntity.hankeKayttaja

        permissionService.findPermission(kayttaja.hankeId, userId)?.let { permission ->
            throw UserAlreadyHasPermissionException(userId, kayttaja.id, permission.id)
        }

        kayttaja.permission?.let { permission ->
            throw PermissionAlreadyExistsException(
                userId,
                permission.userId,
                kayttaja.id,
                permission.id
            )
        }

        kayttaja.permission =
            permissionService.create(kayttaja.hankeId, userId, tunnisteEntity.kayttooikeustaso)

        kayttaja.kayttajaTunniste = null
        kayttajaTunnisteRepository.delete(tunnisteEntity)
        logService.logDelete(tunnisteEntity.toDomain(), userId)

        return kayttaja.toDomain()
    }

    @Transactional
    fun resendInvitation(kayttajaId: UUID, currentUserId: String) {
        // Re-get the kayttaja under the transaction
        val kayttaja = hankeKayttajaRepository.getReferenceById(kayttajaId)
        kayttaja.permission?.let {
            throw UserAlreadyHasPermissionException(currentUserId, kayttaja.id, it.id)
        }
        val inviter =
            getKayttajaByUserId(kayttaja.hankeId, currentUserId)
                ?: throw CurrentUserWithoutKayttajaException(currentUserId)

        recreateTunniste(kayttaja, currentUserId)
        val hanke = hankeRepository.getReferenceById(kayttaja.hankeId)
        sendHankeInvitation(hanke.hankeTunnus!!, hanke.nimi!!, inviter, kayttaja)
    }

    /** Check that every user an update was requested for was found as a user of the hanke. */
    private fun validateAllKayttajatFound(
        existingKayttajat: List<HankeKayttajaEntity>,
        requestedUpdates: Map<UUID, Kayttooikeustaso>,
        hankeIdentifier: HankeIdentifier,
    ) {
        with(existingKayttajat.map { it.id }) {
            if (!this.containsAll(requestedUpdates.keys)) {
                val missingIds = requestedUpdates.keys.subtract(this.toSet())
                throw HankeKayttajatNotFoundException(missingIds, hankeIdentifier)
            }
        }
    }

    /**
     * The user needs to have admin permission (KAIKKI_OIKEUDET kayttooikeustaso) whenever removing
     * or adding a KAIKKI_OIKEUDET from users.
     */
    private fun validateAdminPermissionIfNeeded(
        kayttajat: List<HankeKayttajaEntity>,
        updates: Map<UUID, Kayttooikeustaso>,
        deleteAdminPermission: Boolean,
        userId: String,
    ) {
        val currentKayttooikeustasot = currentKayttooikeustasot(kayttajat)

        if (
            (currentKayttooikeustasot.any { (_, kayttooikeustaso) ->
                kayttooikeustaso == Kayttooikeustaso.KAIKKI_OIKEUDET
            } ||
                updates.any { (_, kayttooikeustaso) ->
                    kayttooikeustaso == Kayttooikeustaso.KAIKKI_OIKEUDET
                }) && !deleteAdminPermission
        ) {
            throw MissingAdminPermissionException(userId)
        }
    }

    /**
     * After any change to users' access levels, at least one user with KAIKKI_OIKEUDET
     * kayttooikeustaso in an activated permission needs to remain. Otherwise, there's no one who
     * can add that access level.
     */
    private fun validateAdminRemains(hankeIdentifier: HankeIdentifier) {
        if (
            permissionService.findByHankeId(hankeIdentifier.id).all {
                it.kayttooikeustaso != Kayttooikeustaso.KAIKKI_OIKEUDET
            }
        ) {
            throw NoAdminRemainingException(hankeIdentifier)
        }
    }

    /**
     * Finds the current access levels of the given HankeKayttajat. Returns an ID ->
     * Kayttooikeustaso map.
     */
    private fun currentKayttooikeustasot(
        kayttajat: List<HankeKayttajaEntity>
    ): Map<UUID, Kayttooikeustaso> {
        val currentKayttooikeustasot = kayttajat.map { it.id to it.deriveKayttooikeustaso() }
        currentKayttooikeustasot
            .filter { (_, kayttooikeustaso) -> kayttooikeustaso == null }
            .map { (id, _) -> id }
            .also { missingIds ->
                if (missingIds.isNotEmpty()) {
                    throw UsersWithoutKayttooikeustasoException(missingIds)
                }
            }
        return currentKayttooikeustasot.associate { (id, kayttooikeustaso) ->
            id to kayttooikeustaso!!
        }
    }

    private fun createTunnisteAndKayttaja(
        hankeId: Int,
        hankeTunnus: String,
        hankeNimi: String,
        currentKayttaja: HankeKayttajaEntity?,
        contact: UserContact,
        currentUserId: String
    ) {
        val newHankeUser =
            createUser(
                currentUser = currentUserId,
                hankeId = hankeId,
                nimi = contact.name,
                sahkoposti = contact.email,
            )
        val kayttajaTunnisteEntity = createTunniste(newHankeUser, currentUserId)
        newHankeUser.kayttajaTunniste = kayttajaTunnisteEntity
        sendHankeInvitation(hankeTunnus, hankeNimi, currentKayttaja, newHankeUser)
    }

    private fun updateKayttooikeustaso(
        kayttajaTunnisteEntity: KayttajaTunnisteEntity,
        kayttooikeustaso: Kayttooikeustaso,
        userId: String
    ) {
        val kayttajaTunnisteBefore = kayttajaTunnisteEntity.toDomain()
        kayttajaTunnisteEntity.kayttooikeustaso = kayttooikeustaso
        val kayttajaTunnisteAfter = kayttajaTunnisteEntity.toDomain()
        logger.info {
            "Updated kayttooikeustaso in kayttajatunniste, " +
                "kayttajaTunnisteId=${kayttajaTunnisteEntity.id}, " +
                "new kayttooikeustaso=${kayttooikeustaso}, " +
                "userId=$userId"
        }
        logService.logUpdate(kayttajaTunnisteBefore, kayttajaTunnisteAfter, userId)
    }

    private fun sendHankeInvitation(
        hankeTunnus: String,
        hankeNimi: String,
        inviter: HankeKayttajaEntity?,
        recipient: HankeKayttajaEntity,
    ) {
        logger.info { "Sending Hanke invitation." }

        if (inviter == null) {
            logger.warn { "Inviter kayttaja null, will not send Hanke invitation." }
            return
        }

        emailSenderService.sendHankeInvitationEmail(
            HankeInvitationData(
                inviterName = inviter.nimi,
                inviterEmail = inviter.sahkoposti,
                recipientEmail = recipient.sahkoposti,
                hankeTunnus = hankeTunnus,
                hankeNimi = hankeNimi,
                invitationToken = recipient.kayttajaTunniste!!.tunniste,
            )
        )
    }

    private fun createTunniste(
        hankeKayttaja: HankeKayttajaEntity,
        currentUserId: String,
    ): KayttajaTunnisteEntity {
        logger.info {
            "Creating a new user token, " +
                "hankeKayttajaId=${hankeKayttaja.id}, hankeId=${hankeKayttaja.hankeId}"
        }
        val token = KayttajaTunnisteEntity.create(hankeKayttaja)
        val kayttajaTunnisteEntity = kayttajaTunnisteRepository.save(token)
        logger.info { "Saved the new user token, id=${kayttajaTunnisteEntity.id}" }
        logService.logCreate(kayttajaTunnisteEntity.toDomain(), currentUserId)
        return kayttajaTunnisteEntity
    }

    private fun recreateTunniste(hankeKayttaja: HankeKayttajaEntity, currentUserId: String) {
        hankeKayttaja.kayttajaTunniste?.let { tunniste ->
            logger.info { "Deleting old tunniste ${tunniste.id}" }
            kayttajaTunnisteRepository.delete(tunniste)
            // Flush to avoid unique key collision on hanke_kayttaja_id
            kayttajaTunnisteRepository.flush()
            logService.logDelete(tunniste.toDomain(), currentUserId)
        }
        hankeKayttaja.kayttajaTunniste = createTunniste(hankeKayttaja, currentUserId)
    }

    private fun createUser(
        currentUser: String,
        hankeId: Int,
        nimi: String,
        sahkoposti: String,
        permission: PermissionEntity? = null,
        tunniste: KayttajaTunnisteEntity? = null,
    ): HankeKayttajaEntity {
        val kayttajaEntity =
            hankeKayttajaRepository.save(
                HankeKayttajaEntity(
                    hankeId = hankeId,
                    nimi = nimi,
                    sahkoposti = sahkoposti,
                    permission = permission,
                    kayttajaTunniste = tunniste,
                )
            )
        logger.info { "Saved the user information, id=${kayttajaEntity.id}" }
        logService.logCreate(kayttajaEntity.toDomain(), currentUser)
        return kayttajaEntity
    }

    private fun filterNewContacts(hankeId: Int, contacts: List<UserContact>): List<UserContact> {
        val existingEmails = hankeExistingEmails(hankeId, contacts)

        val newContacts =
            contacts
                .filter { contact -> !existingEmails.contains(contact.email) }
                .distinctBy { it.email }
        logger.info {
            "From ${contacts.size} contacts, there were ${newContacts.size} new contacts."
        }
        return newContacts
    }

    private fun hankeExistingEmails(hankeId: Int, contacts: List<UserContact>): List<String> =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, contacts.map { it.email })
            .map { it.sahkoposti }
}

class HankeKayttajaNotFoundException(kayttajaId: UUID) :
    RuntimeException("HankeKayttaja was not found with ID: $kayttajaId")

class ChangingOwnPermissionException(userId: String) :
    RuntimeException("User tried to change their own permissions, userId=$userId")

class MissingAdminPermissionException(userId: String) :
    RuntimeException("User doesn't have permission to change admin permissions. userId=$userId")

class UsersWithoutKayttooikeustasoException(missingIds: Collection<UUID>) :
    RuntimeException(
        "Some HankeKayttaja have neither permissions nor user tokens, " +
            "their ids = ${missingIds.joinToString()}"
    )

class NoAdminRemainingException(hankeIdentifier: HankeIdentifier) :
    RuntimeException(
        "No one with admin rights would remain after permission changes. ${hankeIdentifier.logString()}"
    )

class HankeKayttajatNotFoundException(
    missingIds: Collection<UUID>,
    hankeIdentifier: HankeIdentifier
) :
    RuntimeException(
        "Some HankeKayttaja were not found. Either the IDs don't exist or they belong to another " +
            "hanke. Missing IDs: ${missingIds.joinToString()}, ${hankeIdentifier.logString()}"
    )

class TunnisteNotFoundException(userId: String, tunniste: String) :
    RuntimeException("A matching token was not found, userId=$userId, tunniste=$tunniste")

class UserAlreadyHasPermissionException(userId: String, kayttajaId: UUID, permissionId: Int) :
    RuntimeException(
        "The user already has an active permission, userId=$userId, hankeKayttajaId=$kayttajaId, permissionsId=$permissionId"
    )

class PermissionAlreadyExistsException(
    currentUserId: String,
    permissionUserId: String,
    hankeKayttajaId: UUID,
    permissionId: Int,
) :
    RuntimeException(
        "Another user has an active permission with the same hanke kayttaja, " +
            "the current user is $currentUserId, the user on the permission is $permissionUserId, " +
            "hankeKayttajaId=$hankeKayttajaId, permissionId=$permissionId"
    )

class CurrentUserWithoutKayttajaException(currentUserId: String) :
    RuntimeException("The current user doesn't have a hankeKayttaja. userId=$currentUserId")
