package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeArgumentException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.Perustaja
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
    private val permissionService: PermissionService,
    private val featureFlags: FeatureFlags,
    private val logService: HankeKayttajaLoggingService,
) {

    @Transactional(readOnly = true)
    fun getKayttajatByHankeId(hankeId: Int): List<HankeKayttajaDto> =
        hankeKayttajaRepository.findByHankeId(hankeId).map { it.toDto() }

    @Transactional
    fun saveNewTokensFromApplication(application: ApplicationEntity, hankeId: Int, userId: String) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }
        logger.info {
            "Creating users and user tokens for application ${application.id}, alluid=${application.alluid}}"
        }
        val contacts =
            application.applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact ->
            createTunnisteAndKayttaja(hankeId, contact, userId)
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
        val contacts =
            hanke
                .extractYhteystiedot()
                .flatMap { it.alikontaktit }
                .mapNotNull { userContactOrNull(it.fullName(), it.email) }

        filterNewContacts(hankeId, contacts).forEach { contact ->
            createTunnisteAndKayttaja(hankeId, contact, userId)
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

    private fun createTunnisteAndKayttaja(hankeId: Int, contact: UserContact, userId: String) {
        val kayttajaTunnisteEntity = saveTunniste(hankeId, userId)
        createUser(
            userId,
            hankeId = hankeId,
            nimi = contact.name,
            sahkoposti = contact.email,
            tunniste = kayttajaTunnisteEntity,
        )
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

    private fun saveTunniste(hankeId: Int, userId: String): KayttajaTunnisteEntity {
        logger.info { "Creating a new user token, hankeId=$hankeId" }
        val token = KayttajaTunnisteEntity.create()
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
    ) {
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
    }

    private fun userContactOrNull(name: String?, email: String?): UserContact? {
        return when {
            name.isNullOrBlank() || email.isNullOrBlank() -> null
            else -> UserContact(name, email)
        }
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

data class UserContact(val name: String, val email: String)

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
