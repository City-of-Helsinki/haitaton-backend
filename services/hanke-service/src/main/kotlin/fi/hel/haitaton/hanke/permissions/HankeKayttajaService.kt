package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeArgumentException
import fi.hel.haitaton.hanke.application.ApplicationArgumentException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.ApplicationUserContact
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeUserContact
import fi.hel.haitaton.hanke.domain.Perustaja
import fi.hel.haitaton.hanke.domain.UserContact
import fi.hel.haitaton.hanke.email.ApplicationInvitationData
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.email.HankeInvitationData
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import fi.hel.haitaton.hanke.logging.HankeKayttajaLoggingService
import fi.hel.haitaton.hanke.removeInviter
import fi.hel.haitaton.hanke.typedContacts
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeKayttajaService(
    private val hankeKayttajaRepository: HankeKayttajaRepository,
    private val kayttajaTunnisteRepository: KayttajaTunnisteRepository,
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
        userId: String
    ) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }
        logger.info {
            "Creating users and user tokens for application ${application.id}, alluid=${application.alluid}}"
        }
        val applicationIdentifier =
            application.applicationIdentifier
                ?: throw ApplicationArgumentException("Application identifier null")

        val inviter = getKayttajaByUserId(hankeId, userId)
        val contacts = application.applicationData.typedContacts().removeInviter(inviter)

        filterNewContacts(hankeId, contacts).forEach { contact ->
            createTunnisteAndKayttaja(hankeId, hankeTunnus, hankeNimi, inviter, contact, userId)
        }
        contacts.forEach { contact ->
            sendApplicationInvitation(
                hankeTunnus,
                applicationIdentifier,
                application.applicationType,
                inviter,
                contact,
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
        hanke: Hanke,
        updates: Map<UUID, Kayttooikeustaso>,
        deleteAdminPermission: Boolean,
        userId: String,
    ) {
        logger.info {
            "Updating permissions for hankekayttajat. hankeId=${hanke.id} hanketunnus=${hanke.hankeTunnus}"
        }

        val kayttajat = hankeKayttajaRepository.findByHankeIdAndIdIn(hanke.id!!, updates.keys)

        if (kayttajat.any { it.permission?.userId == userId }) {
            throw ChangingOwnPermissionException(userId)
        }

        validateAllKayttajatFound(kayttajat, updates, hanke)
        validateAdminPermissionIfNeeded(kayttajat, updates, deleteAdminPermission, userId)

        kayttajat.forEach { kayttaja ->
            kayttaja.permission?.let { permission ->
                permissionService.updateKayttooikeustaso(permission, updates[kayttaja.id]!!, userId)
            }
                ?: kayttaja.kayttajaTunniste?.let { tunniste ->
                    updateKayttooikeustaso(tunniste, updates[kayttaja.id]!!, userId)
                }
        }

        validateAdminRemains(hanke)
    }

    @Transactional
    fun createPermissionFromToken(userId: String, tunniste: String) {
        logger.info { "Trying to activate token $tunniste for user $userId" }
        val tunnisteEntity =
            kayttajaTunnisteRepository.findByTunniste(tunniste)
                ?: throw TunnisteNotFoundException(userId, tunniste)

        val kayttaja =
            tunnisteEntity.hankeKayttaja
                ?: throw OrphanedTunnisteException(userId, tunnisteEntity.id)

        permissionService.findPermission(kayttaja.hankeId, userId)?.let { permission ->
            throw UserAlreadyHasPermissionException(userId, tunnisteEntity.id, permission.id)
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
    }

    /** Check that every user an update was requested for was found as a user of the hanke. */
    private fun validateAllKayttajatFound(
        existingKayttajat: List<HankeKayttajaEntity>,
        requestedUpdates: Map<UUID, Kayttooikeustaso>,
        hanke: Hanke,
    ) {
        with(existingKayttajat.map { it.id }) {
            if (!this.containsAll(requestedUpdates.keys)) {
                val missingIds = requestedUpdates.keys.subtract(this.toSet())
                throw HankeKayttajatNotFoundException(missingIds, hanke)
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
    private fun validateAdminRemains(hanke: Hanke) {
        if (
            permissionService.findByHankeId(hanke.id!!).all {
                it.kayttooikeustaso != Kayttooikeustaso.KAIKKI_OIKEUDET
            }
        ) {
            throw NoAdminRemainingException(hanke)
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
        inviter: HankeKayttajaEntity?,
        contact: UserContact,
        userId: String
    ) {
        val kayttajaTunnisteEntity = createTunniste(hankeId, userId)
        val newHankeUser =
            createUser(
                currentUser = userId,
                hankeId = hankeId,
                nimi = contact.name,
                sahkoposti = contact.email,
                tunniste = kayttajaTunnisteEntity,
            )
        sendHankeInvitation(hankeTunnus, hankeNimi, inviter, newHankeUser)
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
            logger.warn { "Inviter kaytaja null, will not send Hanke invitation." }
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

    private fun sendApplicationInvitation(
        hankeTunnus: String,
        applicationIdentifier: String,
        applicationType: ApplicationType,
        inviter: HankeKayttajaEntity?,
        recipient: ApplicationUserContact
    ) {
        logger.info { "Sending Application invitation." }

        if (inviter == null) {
            logger.warn { "Inviter kaytaja null, will not send application invitation." }
            return
        }

        emailSenderService.sendApplicationInvitationEmail(
            ApplicationInvitationData(
                inviterName = inviter.nimi,
                inviterEmail = inviter.sahkoposti,
                recipientEmail = recipient.email,
                hankeTunnus = hankeTunnus,
                applicationIdentifier = applicationIdentifier,
                applicationType = applicationType,
                roleType = recipient.type,
            )
        )
    }

    private fun createTunniste(
        hankeId: Int,
        userId: String,
    ): KayttajaTunnisteEntity {
        logger.info { "Creating a new user token, hankeId=$hankeId" }
        val token = KayttajaTunnisteEntity.create(sentAt = getCurrentTimeUTC().toOffsetDateTime())
        val kayttajaTunnisteEntity = kayttajaTunnisteRepository.save(token)
        logger.info { "Saved the new user token, id=${kayttajaTunnisteEntity.id}" }
        logService.logCreate(kayttajaTunnisteEntity.toDomain(), userId)
        return kayttajaTunnisteEntity
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

    private fun <T : UserContact> filterNewContacts(
        hankeId: Int,
        contacts: Collection<T>
    ): List<T> {
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

    private fun hankeExistingEmails(hankeId: Int, contacts: Collection<UserContact>): List<String> =
        hankeKayttajaRepository
            .findByHankeIdAndSahkopostiIn(hankeId, contacts.map { it.email })
            .map { it.sahkoposti }
}

class ChangingOwnPermissionException(userId: String) :
    RuntimeException("User tried to change their own permissions, userId=$userId")

class MissingAdminPermissionException(userId: String) :
    RuntimeException("User doesn't have permission to change admin permissions. userId=$userId")

class UsersWithoutKayttooikeustasoException(missingIds: Collection<UUID>) :
    RuntimeException(
        "Some HankeKayttaja have neither permissions nor user tokens, " +
            "their ids = ${missingIds.joinToString()}"
    )

class NoAdminRemainingException(hanke: Hanke) :
    RuntimeException(
        "No one with admin rights would remain after permission changes. hankeId=${hanke.id}, hanketunnus=${hanke.hankeTunnus}"
    )

class HankeKayttajatNotFoundException(missingIds: Collection<UUID>, hanke: Hanke) :
    RuntimeException(
        "Some HankeKayttaja were not found. Either the IDs don't exist or they belong to another " +
            "hanke. Missing IDs: ${missingIds.joinToString()}, hankeId=${hanke.id}, hanketunnus=${hanke.hankeTunnus}"
    )

class TunnisteNotFoundException(userId: String, tunniste: String) :
    RuntimeException("A matching token was not found, userId=$userId, tunniste=$tunniste")

class OrphanedTunnisteException(userId: String, tunnisteId: UUID) :
    RuntimeException(
        "A token didn't have a matching user, userId=$userId, kayttajaTunnisteId=$tunnisteId"
    )

class UserAlreadyHasPermissionException(userId: String, tunnisteId: UUID, permissionId: Int) :
    RuntimeException(
        "A user already had an active permission, userId=$userId, kayttajaTunnisteId=$tunnisteId, permissionsId=$permissionId"
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
