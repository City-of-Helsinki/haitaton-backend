package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.Hanke
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
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val kayttajakutsuRepository: KayttajakutsuRepository,
    private val hankeRepository: HankeRepository,
    private val permissionService: PermissionService,
    private val featureFlags: FeatureFlags,
    private val logService: HankeKayttajaLoggingService,
    private val emailSenderService: EmailSenderService,
) {
    @Transactional(readOnly = true)
    fun getKayttajatByHankeId(hankeId: Int): List<HankeKayttajaDto> =
        hankekayttajaRepository.findByHankeId(hankeId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getKayttajaByUserId(hankeId: Int, userId: String): HankekayttajaEntity? {
        val permission = permissionService.findPermission(hankeId, userId)
        if (permission == null) {
            logger.warn {
                "UserId=$userId does not have a permission instance for HankeId=$hankeId"
            }
            return null
        }

        val hankeKayttaja = hankekayttajaRepository.findByPermissionId(permission.id)
        if (hankeKayttaja == null) {
            logger.warn { "No kayttaja instance found (hankeId=$hankeId, userId=$userId) " }
            return null
        }

        return hankeKayttaja
    }

    @Transactional
    fun createNewUser(
        request: NewUserRequest,
        hanke: Hanke,
        currentUserId: String
    ): HankeKayttajaDto {
        if (
            hankekayttajaRepository
                .findByHankeId(hanke.id)
                .map { it.sahkoposti }
                .contains(request.sahkoposti)
        ) {
            throw UserAlreadyExistsException(hanke, request.sahkoposti)
        }

        val inviter = getKayttajaByUserId(hanke.id, currentUserId)

        return createKutsuAndKayttaja(
                hanke.id,
                hanke.hankeTunnus,
                hanke.nimi,
                inviter,
                request.toDomain(),
                currentUserId
            )
            .toDto()
    }

    @Transactional
    fun saveNewTokensFromApplication(
        application: ApplicationEntity,
        hankeId: Int,
        hankeTunnus: String,
        hankeNimi: String,
        currentUserId: String,
        currentKayttaja: HankekayttajaEntity? = null,
    ) {
        logger.info {
            "Creating users and user tokens for application ${application.id}, alluid=${application.alluid}}"
        }

        val kayttajaInput =
            application.applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { it.toHankekayttajaInput() }

        filterNewKayttajas(hankeId, kayttajaInput).forEach {
            createKutsuAndKayttaja(
                hankeId = hankeId,
                hankeTunnus = hankeTunnus,
                hankeNimi = hankeNimi,
                inviter = currentKayttaja,
                kayttaja = it,
                currentUserId = currentUserId,
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

        val kayttajaInput =
            hanke
                .extractYhteystiedot()
                .flatMap { it.alikontaktit }
                .mapNotNull { it.toHankekayttajaInput() }

        val inviter = getKayttajaByUserId(hanke.id, userId)
        filterNewKayttajas(hanke.id, kayttajaInput).forEach {
            createKutsuAndKayttaja(
                hankeId = hanke.id,
                hankeTunnus = hanke.hankeTunnus,
                hankeNimi = hanke.nimi,
                inviter = inviter,
                kayttaja = it,
                currentUserId = userId,
            )
        }
    }

    @Transactional
    fun addHankeFounder(hankeId: Int, hankeFounder: HankekayttajaInput?, currentUserId: String) {
        val permissionEntity =
            permissionService.create(hankeId, currentUserId, Kayttooikeustaso.KAIKKI_OIKEUDET)

        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }
        hankeFounder?.let {
            logger.info { "Saving user for Hanke founder." }
            createFounderKayttaja(
                currentUserId = currentUserId,
                hankeId = hankeId,
                founder = hankeFounder,
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
            hankekayttajaRepository.findByHankeIdAndIdIn(hankeIdentifier.id, updates.keys)

        if (kayttajat.any { it.permission?.userId == userId }) {
            throw ChangingOwnPermissionException(userId)
        }

        validateAllKayttajatFound(kayttajat, updates, hankeIdentifier)
        validateAdminPermissionIfNeeded(kayttajat, updates, deleteAdminPermission, userId)

        kayttajat.forEach { kayttaja ->
            kayttaja.permission?.let { permission ->
                permissionService.updateKayttooikeustaso(permission, updates[kayttaja.id]!!, userId)
            }
                ?: kayttaja.kayttajakutsu?.let { tunniste ->
                    updateKayttooikeustaso(tunniste, updates[kayttaja.id]!!, userId)
                }
        }

        validateAdminRemains(hankeIdentifier)
    }

    @Transactional
    fun createPermissionFromToken(userId: String, tunniste: String): HankeKayttaja {
        logger.info { "Trying to activate token $tunniste for user $userId" }
        val tunnisteEntity =
            kayttajakutsuRepository.findByTunniste(tunniste)
                ?: throw TunnisteNotFoundException(userId, tunniste)

        val kayttaja = tunnisteEntity.hankekayttaja

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

        kayttaja.kayttajakutsu = null
        kayttajakutsuRepository.delete(tunnisteEntity)
        logService.logDelete(tunnisteEntity.toDomain(), userId)

        return kayttaja.toDomain()
    }

    @Transactional
    fun resendInvitation(kayttajaId: UUID, currentUserId: String) {
        // Re-get the kayttaja under the transaction
        val kayttaja = hankekayttajaRepository.getReferenceById(kayttajaId)
        kayttaja.permission?.let {
            throw UserAlreadyHasPermissionException(currentUserId, kayttaja.id, it.id)
        }
        val inviter =
            getKayttajaByUserId(kayttaja.hankeId, currentUserId)
                ?: throw CurrentUserWithoutKayttajaException(currentUserId)

        recreateKutsu(kayttaja, currentUserId)
        val hanke = hankeRepository.getReferenceById(kayttaja.hankeId)
        sendHankeInvitation(hanke.hankeTunnus, hanke.nimi, inviter, kayttaja)
    }

    /** Check that every user an update was requested for was found as a user of the hanke. */
    private fun validateAllKayttajatFound(
        existingKayttajat: List<HankekayttajaEntity>,
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
        kayttajat: List<HankekayttajaEntity>,
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
        kayttajat: List<HankekayttajaEntity>
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

    private fun createKutsuAndKayttaja(
        hankeId: Int,
        hankeTunnus: String,
        hankeNimi: String,
        inviter: HankekayttajaEntity?,
        kayttaja: HankekayttajaInput,
        currentUserId: String
    ): HankekayttajaEntity {
        val newHankeUser =
            createKayttaja(
                currentUserId = currentUserId,
                hankeId = hankeId,
                kayttaja = kayttaja,
                inviterId = inviter?.id,
            )
        val kutsuEntity = createKutsu(newHankeUser, currentUserId)
        newHankeUser.kayttajakutsu = kutsuEntity
        sendHankeInvitation(hankeTunnus, hankeNimi, inviter, newHankeUser)
        return newHankeUser
    }

    private fun updateKayttooikeustaso(
        kayttajakutsuEntity: KayttajakutsuEntity,
        kayttooikeustaso: Kayttooikeustaso,
        userId: String
    ) {
        val kayttajakutsuBefore = kayttajakutsuEntity.toDomain()
        kayttajakutsuEntity.kayttooikeustaso = kayttooikeustaso
        val kayttajakutsuAfter = kayttajakutsuEntity.toDomain()
        logger.info {
            "Updated kayttooikeustaso in kayttajakutsu, " +
                "kayttajakutsuId=${kayttajakutsuEntity.id}, " +
                "new kayttooikeustaso=${kayttooikeustaso}, " +
                "userId=$userId"
        }
        logService.logUpdate(kayttajakutsuBefore, kayttajakutsuAfter, userId)
    }

    private fun sendHankeInvitation(
        hankeTunnus: String,
        hankeNimi: String,
        inviter: HankekayttajaEntity?,
        recipient: HankekayttajaEntity,
    ) {
        logger.info { "Sending Hanke invitation." }

        if (inviter == null) {
            logger.warn { "Inviter kayttaja null, will not send Hanke invitation." }
            return
        }

        emailSenderService.sendHankeInvitationEmail(
            HankeInvitationData(
                inviterName = inviter.fullName(),
                inviterEmail = inviter.sahkoposti,
                recipientEmail = recipient.sahkoposti,
                hankeTunnus = hankeTunnus,
                hankeNimi = hankeNimi,
                invitationToken = recipient.kayttajakutsu!!.tunniste,
            )
        )
    }

    private fun createKutsu(
        kayttaja: HankekayttajaEntity,
        currentUserId: String,
    ): KayttajakutsuEntity {
        logger.info {
            "Creating a new user token, " +
                "hankeKayttajaId=${kayttaja.id}, hankeId=${kayttaja.hankeId}"
        }
        val token = KayttajakutsuEntity.create(kayttaja)
        val kutsuEntity = kayttajakutsuRepository.save(token)
        logger.info { "Saved the new user token, id=${kutsuEntity.id}" }
        logService.logCreate(kutsuEntity.toDomain(), currentUserId)
        return kutsuEntity
    }

    private fun recreateKutsu(kayttaja: HankekayttajaEntity, currentUserId: String) {
        kayttaja.kayttajakutsu?.let { tunniste ->
            logger.info { "Deleting old tunniste ${tunniste.id}" }
            kayttajakutsuRepository.delete(tunniste)
            // Flush to avoid unique key collision on hanke_kayttaja_id
            kayttajakutsuRepository.flush()
            logService.logDelete(tunniste.toDomain(), currentUserId)
        }
        kayttaja.kayttajakutsu = createKutsu(kayttaja, currentUserId)
    }

    private fun createFounderKayttaja(
        currentUserId: String,
        hankeId: Int,
        founder: HankekayttajaInput,
        permission: PermissionEntity,
    ) =
        saveHankekayttaja(
            kayttaja =
                HankekayttajaEntity(
                    hankeId = hankeId,
                    etunimi = founder.etunimi,
                    sukunimi = founder.sukunimi,
                    sahkoposti = founder.email,
                    puhelin = founder.puhelin,
                    permission = permission,
                ),
            userId = currentUserId,
        )

    private fun createKayttaja(
        currentUserId: String,
        hankeId: Int,
        kayttaja: HankekayttajaInput,
        inviterId: UUID?,
    ): HankekayttajaEntity =
        saveHankekayttaja(
            kayttaja =
                HankekayttajaEntity(
                    hankeId = hankeId,
                    etunimi = kayttaja.etunimi, // updated with actual when user signs in
                    sukunimi = kayttaja.sukunimi, // updated with actual when user signs in
                    kutsuttuEtunimi = kayttaja.etunimi, // name in invitation
                    kutsuttuSukunimi = kayttaja.sukunimi, // name in invitation
                    sahkoposti = kayttaja.email,
                    puhelin = kayttaja.puhelin,
                    permission = null, // set at first sign in
                    kutsujaId = inviterId,
                ),
            userId = currentUserId,
        )

    private fun saveHankekayttaja(kayttaja: HankekayttajaEntity, userId: String) =
        hankekayttajaRepository.save(kayttaja).also {
            logger.info { "Saved the user information, id=${kayttaja.id}" }
            logService.logCreate(kayttaja.toDomain(), userId)
        }

    private fun filterNewKayttajas(
        hankeId: Int,
        kayttajas: List<HankekayttajaInput>
    ): List<HankekayttajaInput> {
        val existingEmails = hankeExistingEmails(hankeId, kayttajas.map { it.email })

        return kayttajas
            .filter { contact -> !existingEmails.contains(contact.email) }
            .distinctBy { it.email }
            .also {
                logger.info {
                    "From ${kayttajas.size} contacts, there were ${it.size} new contacts."
                }
            }
    }

    private fun hankeExistingEmails(hankeId: Int, emails: List<String>): List<String> =
        hankekayttajaRepository.findByHankeIdAndSahkopostiIn(hankeId, emails).map { it.sahkoposti }
}

class UserAlreadyExistsException(hankeIdentifier: HankeIdentifier, sahkoposti: String) :
    RuntimeException(
        "Hankekayttaja with that email was already present in the hanke. sahkoposti=$sahkoposti ${hankeIdentifier.logString()} "
    )

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
