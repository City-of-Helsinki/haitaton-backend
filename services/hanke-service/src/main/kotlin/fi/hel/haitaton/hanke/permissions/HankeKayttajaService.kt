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
    private val roleRepository: RoleRepository,
    private val permissionService: PermissionService,
    private val featureFlags: FeatureFlags,
    private val logService: HankeKayttajaLoggingService,
) {

    @Transactional(readOnly = true)
    fun getKayttajatByHankeId(hankeId: Int): List<HankeKayttajaDto> =
        hankeKayttajaRepository.findByHankeId(hankeId).map { it.toDto() }

    @Transactional
    fun saveNewTokensFromApplication(application: ApplicationEntity, hankeId: Int) {
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

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
    }

    @Transactional
    fun saveNewTokensFromHanke(hanke: Hanke) {
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

        filterNewContacts(hankeId, contacts).forEach { contact -> createToken(hankeId, contact) }
    }

    @Transactional
    fun addHankeFounder(hankeId: Int, founder: Perustaja, permissionEntity: PermissionEntity) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            return
        }
        logger.info { "Saving user for Hanke perustaja." }
        saveUser(
            HankeKayttajaEntity(
                hankeId = hankeId,
                nimi = founder.nimi!!,
                sahkoposti = founder.email,
                permission = permissionEntity,
                kayttajaTunniste = null,
            )
        )
    }

    @Transactional
    fun updatePermissions(
        hanke: Hanke,
        updates: Map<UUID, Role>,
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
            if (kayttaja.permission != null) {
                val roleBefore = kayttaja.permission.role.role
                kayttaja.permission.role = roleRepository.findOneByRole(updates[kayttaja.id]!!)
                logService.logUpdate(roleBefore, kayttaja.permission.toDomain(), userId)
            } else {
                val kayttajaTunnisteBefore = kayttaja.kayttajaTunniste!!.toDomain()
                kayttaja.kayttajaTunniste.role = updates[kayttaja.id]!!
                val kayttajaTunnisteAfter = kayttaja.kayttajaTunniste.toDomain()
                logService.logUpdate(kayttajaTunnisteBefore, kayttajaTunnisteAfter, userId)
            }
        }

        validateAdminRemains(hanke)
    }

    /** Check that every user an update was requested for was found as a user of the hanke. */
    private fun validateAllKayttajatFound(
        existingKayttajat: List<HankeKayttajaEntity>,
        requestedUpdates: Map<UUID, Role>,
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
     * The user needs to have admin permission (KAIKKI_OIKEUDET role) whenever removing or adding a
     * KAIKKI_OIKEUDET from users.
     */
    private fun validateAdminPermissionIfNeeded(
        kayttajat: List<HankeKayttajaEntity>,
        updates: Map<UUID, Role>,
        deleteAdminPermission: Boolean,
        userId: String,
    ) {
        val currentRoles = currentRoles(kayttajat)

        if (
            (currentRoles.any { (_, role) -> role == Role.KAIKKI_OIKEUDET } ||
                updates.any { (_, role) -> role == Role.KAIKKI_OIKEUDET }) && !deleteAdminPermission
        ) {
            throw MissingAdminPermissionException(userId)
        }
    }

    /**
     * After any change to users' roles, at least one user with KAIKKI_OIKEUDET role in an activated
     * permission needs to remain. Otherwise, there's no one who can add that role.
     */
    private fun validateAdminRemains(hanke: Hanke) {
        if (
            permissionService.findByHankeId(hanke.id!!).all { it.role.role != Role.KAIKKI_OIKEUDET }
        ) {
            throw NoAdminRemainingException(hanke)
        }
    }

    /** Finds the current roles of the given HankeKayttajat. Returns an ID -> Role map with */
    private fun currentRoles(kayttajat: List<HankeKayttajaEntity>): Map<UUID, Role> {
        val currentRoles = kayttajat.map { it.id to it.deriveRole() }
        currentRoles
            .filter { (_, role) -> role == null }
            .map { (id, _) -> id }
            .also { missingIds ->
                if (missingIds.isNotEmpty()) {
                    throw UsersWithoutRolesException(missingIds)
                }
            }
        return currentRoles.associate { (id, role) -> id to role!! }
    }

    private fun createToken(hankeId: Int, contact: UserContact) {
        logger.info { "Creating a new user token, hankeId=$hankeId" }
        val token = KayttajaTunnisteEntity.create()
        val kayttajaTunnisteEntity = kayttajaTunnisteRepository.save(token)
        logger.info { "Saved the new user token, id=${kayttajaTunnisteEntity.id}" }

        saveUser(
            HankeKayttajaEntity(
                hankeId = hankeId,
                nimi = contact.name,
                sahkoposti = contact.email,
                permission = null,
                kayttajaTunniste = kayttajaTunnisteEntity
            )
        )
    }

    private fun saveUser(hankeKayttajaEntity: HankeKayttajaEntity) {
        val user = hankeKayttajaRepository.save(hankeKayttajaEntity)
        logger.info { "Saved the user information, id=${user.id}" }
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

class UsersWithoutRolesException(missingIds: Collection<UUID>) :
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
