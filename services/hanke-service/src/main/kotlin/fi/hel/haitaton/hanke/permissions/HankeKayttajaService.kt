package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.email.AccessRightsUpdateNotificationEmail
import fi.hel.haitaton.hanke.email.HankeInvitationEmail
import fi.hel.haitaton.hanke.logging.HankeKayttajaLoggingService
import fi.hel.haitaton.hanke.profiili.ProfiiliService
import fi.hel.haitaton.hanke.userId
import java.util.UUID
import mu.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeKayttajaService(
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val kayttajakutsuRepository: KayttajakutsuRepository,
    private val hankeRepository: HankeRepository,
    private val permissionService: PermissionService,
    private val logService: HankeKayttajaLoggingService,
    private val profiiliService: ProfiiliService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun getKayttaja(kayttajaId: UUID): HankeKayttaja =
        hankekayttajaRepository.findByIdOrNull(kayttajaId)?.toDomain()
            ?: throw HankeKayttajaNotFoundException(kayttajaId)

    @Transactional(readOnly = true)
    fun hasPermission(hankeKayttaja: HankekayttajaEntity, permission: PermissionCode): Boolean =
        hankeKayttaja.deriveKayttooikeustaso()?.let {
            permissionService.hasPermission(it, permission)
        } ?: false

    @Transactional(readOnly = true)
    fun getKayttajatByHankeId(hankeId: Int): List<HankeKayttajaDto> =
        hankekayttajaRepository.findByHankeId(hankeId).map { it.toDto() }

    @Transactional
    fun getHankeKayttajatWithPermission(
        hankeId: Int,
        permission: PermissionCode,
    ): List<HankeKayttaja> =
        hankekayttajaRepository
            .findByHankeId(hankeId)
            .filter { hasPermission(it, permission) }
            .map { it.toDomain() }

    @Transactional(readOnly = true)
    fun getKayttajaForHanke(kayttajaId: UUID, hankeId: Int): HankekayttajaEntity {
        val kayttaja =
            hankekayttajaRepository.findByIdOrNull(kayttajaId)
                ?: throw HankeKayttajaNotFoundException(kayttajaId)
        if (kayttaja.hankeId != hankeId) {
            throw HankeKayttajaNotFoundException(kayttajaId)
        }
        return kayttaja
    }

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
        currentUserId: String,
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
                request.toHankekayttajaInput(),
                currentUserId,
            )
            .toDto()
    }

    @Transactional
    fun addHankeFounder(
        hankeId: Int,
        hankePerustaja: HankePerustaja,
        securityContext: SecurityContext,
    ) {
        val permissionEntity =
            permissionService.create(
                hankeId,
                securityContext.userId(),
                Kayttooikeustaso.KAIKKI_OIKEUDET,
            )

        val names = profiiliService.getVerifiedName(securityContext)
        val kayttaja =
            HankekayttajaInput(
                names.givenName,
                names.lastName,
                hankePerustaja.sahkoposti,
                hankePerustaja.puhelinnumero,
            )

        logger.info { "Saving user for Hanke founder." }
        createFounderKayttaja(
            currentUserId = securityContext.userId(),
            hankeId = hankeId,
            founder = kayttaja,
            permission = permissionEntity,
        )
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

        getKayttajaByUserId(hankeIdentifier.id, userId)?.let { updater ->
            sendAccessRightsUpdateNotificationEmails(
                hankeIdentifier,
                kayttajat.map { Pair(it, updates[it.id]!!) },
                updater,
            )
        }
    }

    @Transactional
    fun createPermissionFromToken(
        userId: String,
        tunniste: String,
        securityContext: SecurityContext,
    ): HankeKayttaja {
        logger.info { "Trying to activate token $tunniste for user $userId" }
        val tunnisteEntity =
            kayttajakutsuRepository.findByTunniste(tunniste)
                ?: throw TunnisteNotFoundException(userId, tunniste)

        val kayttaja = tunnisteEntity.hankekayttaja

        if (updateVerifiedName(kayttaja, securityContext)) {
            logger.info { "Updated user's name from Profiili. userId = $userId" }
        }

        permissionService.findPermission(kayttaja.hankeId, userId)?.let { permission ->
            throw UserAlreadyHasPermissionException(userId, kayttaja.id, permission.id)
        }

        kayttaja.permission?.let { permission ->
            throw PermissionAlreadyExistsException(
                userId,
                permission.userId,
                kayttaja.id,
                permission.id,
            )
        }

        kayttaja.permission =
            permissionService.create(kayttaja.hankeId, userId, tunnisteEntity.kayttooikeustaso)

        kayttaja.kayttajakutsu = null
        kayttajakutsuRepository.delete(tunnisteEntity)
        logService.logDelete(tunnisteEntity.toDomain(), userId)

        return kayttaja.toDomain()
    }

    private fun updateVerifiedName(
        kayttaja: HankekayttajaEntity,
        securityContext: SecurityContext,
    ): Boolean {
        val (_, lastName, givenName) = profiiliService.getVerifiedName(securityContext)
        return if (givenName != kayttaja.etunimi || lastName != kayttaja.sukunimi) {
            kayttaja.etunimi = givenName
            kayttaja.sukunimi = lastName
            true
        } else false
    }

    @Transactional
    fun resendInvitation(kayttajaId: UUID, currentUserId: String): HankeKayttaja {
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
        return hankekayttajaRepository.getReferenceById(kayttajaId).toDomain()
    }

    @Transactional
    fun updateOwnContactInfo(
        hankeTunnus: String,
        update: ContactUpdate,
        currentUserId: String,
    ): HankeKayttaja {
        hankeRepository
            .findOneByHankeTunnus(hankeTunnus)
            ?.let { getKayttajaByUserId(it.id, currentUserId) }
            ?.let {
                it.sahkoposti = update.sahkoposti
                it.puhelin = update.puhelinnumero
                return it.toDomain()
            } ?: throw HankeNotFoundException(hankeTunnus)
    }

    @Transactional
    fun updateKayttajaInfo(
        hankeTunnus: String,
        update: KayttajaUpdate,
        userId: UUID,
    ): HankeKayttaja {
        val hankeKayttajaEntity =
            hankeRepository.findOneByHankeTunnus(hankeTunnus)?.let {
                getKayttajaForHanke(userId, it.id)
            } ?: throw HankeNotFoundException(hankeTunnus)

        // changing name is not allowed if the user is identified (has a permission)
        if (
            hankeKayttajaEntity.permission != null &&
                (!update.etunimi.isNullOrBlank() || !update.sukunimi.isNullOrBlank())
        ) {
            throw UserAlreadyHasPermissionException(
                userId.toString(),
                hankeKayttajaEntity.id,
                hankeKayttajaEntity.permission!!.id,
            )
        }

        hankeKayttajaEntity.sahkoposti = update.sahkoposti
        hankeKayttajaEntity.puhelin = update.puhelinnumero
        if (!update.etunimi.isNullOrBlank()) hankeKayttajaEntity.etunimi = update.etunimi
        if (!update.sukunimi.isNullOrBlank()) hankeKayttajaEntity.sukunimi = update.sukunimi

        return hankeKayttajaEntity.toDomain()
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
        currentUserId: String,
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
        userId: String,
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

        applicationEventPublisher.publishEvent(
            HankeInvitationEmail(
                inviterName = inviter.fullName(),
                inviterEmail = inviter.sahkoposti,
                to = recipient.sahkoposti,
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

    private fun sendAccessRightsUpdateNotificationEmails(
        hankeIdentifier: HankeIdentifier,
        usersAndPermissions: List<Pair<HankekayttajaEntity, Kayttooikeustaso>>,
        updater: HankekayttajaEntity,
    ) {
        logger.info { "Sending access rights update notification." }
        val hanke =
            hankeRepository.findByIdOrNull(hankeIdentifier.id)
                ?: throw HankeNotFoundException(hankeIdentifier.hankeTunnus)
        usersAndPermissions.forEach { (kayttaja, kayttooikeustaso) ->
            val notificationData =
                AccessRightsUpdateNotificationEmail(
                    kayttaja.sahkoposti,
                    hanke.hankeTunnus,
                    hanke.nimi,
                    updater.fullName(),
                    updater.sahkoposti,
                    kayttooikeustaso,
                )
            applicationEventPublisher.publishEvent(notificationData)
        }
    }
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
    hankeIdentifier: HankeIdentifier,
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
