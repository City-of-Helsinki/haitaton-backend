package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.PermissionRepository
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

interface GdprService {
    @Transactional(readOnly = true) fun findGdprInfo(userId: String): CollectionNode?

    @Transactional(readOnly = true) fun findApplicationsToDelete(userId: String): List<Application>

    @Transactional fun deleteApplications(applicationsToDelete: List<Application>, userId: String)
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
) : GdprService {
    override fun findGdprInfo(userId: String): CollectionNode? {
        val permissionsIds = permissionRepository.findAllByUserId(userId).map { it.id }
        val kayttajat = hankekayttajaRepository.findByPermissionIdIn(permissionsIds)

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

    override fun findApplicationsToDelete(userId: String): List<Application> {
        TODO("Will be implemented in HAI-2338")
    }

    override fun deleteApplications(applicationsToDelete: List<Application>, userId: String) {
        TODO("Will be implemented in HAI-2338")
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
    override fun findApplicationsToDelete(userId: String): List<Application> {
        logger.info { "Finding GDPR information to delete for user $userId" }

        val (pendingApplications, activeApplications) =
            applicationService.getAllApplicationsCreatedByUser(userId).partition {
                applicationService.isStillPending(it.alluid, it.alluStatus)
            }

        if (activeApplications.isNotEmpty()) {
            throw DeleteForbiddenException(activeApplications)
        }

        return pendingApplications
    }

    @Transactional
    override fun deleteApplications(applicationsToDelete: List<Application>, userId: String) {
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
