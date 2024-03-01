package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionRepository
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

interface GdprService {
    @Transactional(readOnly = true) fun findGdprInfo(userId: String): CollectionNode?

    @Transactional(readOnly = true) fun canDelete(userId: String): Boolean

    @Transactional fun deleteInfo(userId: String)
}

@Service
@ConditionalOnProperty(
    name = ["haitaton.features.user-management"],
    havingValue = "true",
    matchIfMissing = false
)
class KortistoGdprService(
    private val permissionRepository: PermissionRepository,
    private val hankekayttajaRepository: HankekayttajaRepository,
    private val hankeRepository: HankeRepository,
) : GdprService {
    @Transactional(readOnly = true)
    override fun findGdprInfo(userId: String): CollectionNode? {
        val kayttajat = findKayttajat(userId)

        val hankeyhteystiedot =
            kayttajat
                .flatMap { it.yhteyshenkilot }
                .map { it.hankeYhteystieto }
                .filter { it.tyyppi != YhteystietoTyyppi.YKSITYISHENKILO }
        val hakemusyhteystiedot =
            kayttajat
                .flatMap { it.hakemusyhteyshenkilot }
                .map { it.hakemusyhteystieto }
                .filter { it.tyyppi != CustomerType.PERSON }

        return GdprJsonConverter.createGdprJson(
            kayttajat.map { it.toDomain() },
            hankeyhteystiedot.map { it.toDomain() },
            hakemusyhteystiedot.map { it.toDomain() },
            userId,
        )
    }

    @Transactional(readOnly = true)
    override fun canDelete(userId: String): Boolean {
        logger.info { "Checking if we can delete information for user $userId" }

        val errors =
            findKayttajat(userId)
                .flatMap { kayttaja ->
                    activeApplicationErrors(kayttaja) +
                        onlyAdminInHankeWithActiveApplications(kayttaja)
                }
                .toSet()

        if (errors.isNotEmpty()) {
            throw DeleteForbiddenException(errors.toList())
        }
        return true
    }

    override fun deleteInfo(userId: String) {
        TODO("Will be implemented in HAI-2338")
    }

    private fun findKayttajat(userId: String): List<HankekayttajaEntity> {
        val permissionsIds = permissionRepository.findAllByUserId(userId).map { it.id }
        return hankekayttajaRepository.findByPermissionIdIn(permissionsIds)
    }

    /**
     * Returns GdprErrors for the applications of the hanke the kayttaja belongs to, if the kayttaja
     * is the only admin (user with KAIKKI_OIKEUDET privileges) in the hanke.
     */
    private fun onlyAdminInHankeWithActiveApplications(
        kayttaja: HankekayttajaEntity
    ): List<GdprError> {
        if (!isAdmin(kayttaja) || hankeHasOtherAdmins(kayttaja)) {
            return listOf()
        }

        return hankeApplicationErrors(kayttaja)
    }

    /**
     * Returns GdprErrors if there are active applications on the hanke the kayttaja belongs to,
     * whether the kayttaja is a contact in those applications or not.
     */
    private fun hankeApplicationErrors(kayttaja: HankekayttajaEntity): List<GdprError> {
        val activeApplications =
            hankeRepository.getReferenceById(kayttaja.hankeId).hakemukset.filter {
                it.alluid != null
            }
        return activeApplications.map { GdprError.fromSentApplication(it.applicationIdentifier) }
    }

    private fun hankeHasOtherAdmins(kayttaja: HankekayttajaEntity): Boolean =
        otherUsers(kayttaja).any(this::isAdmin)

    private fun otherUsers(kayttaja: HankekayttajaEntity): List<PermissionEntity> {
        val hankePermissions = permissionRepository.findAllByHankeId(kayttaja.hankeId)
        return hankePermissions.filter { it.userId != kayttaja.permission!!.userId }
    }

    private fun isAdmin(kayttaja: HankekayttajaEntity): Boolean = isAdmin(kayttaja.permission!!)

    private fun isAdmin(permission: PermissionEntity): Boolean =
        permission.kayttooikeustaso == Kayttooikeustaso.KAIKKI_OIKEUDET

    private fun activeApplicationErrors(kayttaja: HankekayttajaEntity): List<GdprError> {
        val activeApplications =
            kayttaja.hakemusyhteyshenkilot
                .map { it.hakemusyhteystieto }
                .map { it.application }
                .map { it.toApplication() }
                .filter { it.alluid != null }

        return activeApplications.map { GdprError.fromSentApplication(it.applicationIdentifier) }
    }
}

@Service
@ConditionalOnProperty(
    name = ["haitaton.features.user-management"],
    havingValue = "false",
    matchIfMissing = true
)
class OldGdprService(private val applicationService: ApplicationService) : GdprService {

    @Transactional(readOnly = true)
    override fun findGdprInfo(userId: String): CollectionNode? {
        logger.info { "Finding GDPR information for user $userId" }
        val applications = applicationService.getAllApplicationsCreatedByUser(userId)

        return GdprJsonConverter.createGdprJson(applications, userId)
    }

    @Transactional(readOnly = true)
    override fun canDelete(userId: String): Boolean {
        // Will throw an exception if the information can't be deleted
        findApplicationsToDelete(userId)
        return true
    }

    fun findApplicationsToDelete(userId: String): List<Application> {
        logger.info { "Finding GDPR information to delete for user $userId" }

        val (pendingApplications, activeApplications) =
            applicationService.getAllApplicationsCreatedByUser(userId).partition {
                applicationService.isStillPending(it.alluid, it.alluStatus)
            }

        if (activeApplications.isNotEmpty()) {
            throw DeleteForbiddenException.fromSentApplications(activeApplications)
        }

        return pendingApplications
    }

    @Transactional
    override fun deleteInfo(userId: String) {
        // Find the active applications again, inside the transaction.
        val applicationsToDelete = findApplicationsToDelete(userId)

        if (applicationsToDelete.isEmpty()) {
            logger.info { "No GDPR data found for user $userId" }
            return
        }
        logger.info {
            "In accordance with the GDPR request, deleting all ${applicationsToDelete.size} applications by user $userId"
        }
        applicationsToDelete.forEach {
            // Application service will check the status of every application again.
            // This is not optimal, but this is so rarely used, we can live with it.
            applicationService.deleteWithOrphanGeneratedHankeRemoval(it.id!!, userId)
        }
    }
}
