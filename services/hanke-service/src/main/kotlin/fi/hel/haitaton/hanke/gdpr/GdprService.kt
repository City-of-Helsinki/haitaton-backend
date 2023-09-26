package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class GdprService(private val applicationService: ApplicationService) {

    @Transactional(readOnly = true)
    fun findGdprInfo(userId: String): CollectionNode? {
        logger.info { "Finding GDPR information for user $userId" }
        val applications = applicationService.getAllApplicationsCreatedByUser(userId)

        return GdprJsonConverter.createGdprJson(applications, userId)
    }

    @Transactional(readOnly = true)
    fun findApplicationsToDelete(userId: String): List<Application> {
        logger.info { "Finding GDPR information to delete for user $userId" }

        val (pendingApplications, activeApplications) =
            applicationService.getAllApplicationsCreatedByUser(userId).partition {
                applicationService.isStillPending(it)
            }

        if (activeApplications.isNotEmpty()) {
            throw DeleteForbiddenException(activeApplications)
        }

        return pendingApplications
    }

    @Transactional
    fun deleteApplications(applicationsToDelete: List<Application>, userId: String) {
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
