package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.Status
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"

class ApplicationService(
    private val repo: ApplicationRepository,
    private val cableReportService: CableReportService,
    private val disclosureLogService: DisclosureLogService,
) {

    fun getAllApplicationsForCurrentUser(): List<ApplicationDto> {
        return getAllApplicationsForUser(currentUserId())
    }

    fun getAllApplicationsForUser(userId: String): List<ApplicationDto> {
        return repo.getAllByUserId(userId).map { applicationToDto(it) }
    }

    fun getApplicationById(id: Long) = getById(id)?.let { applicationToDto(it) }

    fun create(application: ApplicationDto): ApplicationDto {
        val alluApplication =
            AlluApplication(
                id = null,
                alluid = null,
                userId = currentUserId(),
                applicationType = application.applicationType,
                applicationData = application.applicationData
            )
        trySendingPendingApplicationToAllu(alluApplication)

        return applicationToDto(repo.save(alluApplication))
    }

    fun updateApplicationData(id: Long, newApplicationData: JsonNode): ApplicationDto? {
        val application = getById(id) ?: return null

        if (!isStillPending(application)) {
            throw IllegalArgumentException("Application already sent")
        }

        application.applicationData = newApplicationData
        trySendingPendingApplicationToAllu(application)

        return applicationToDto(repo.save(application))
    }

    fun sendApplication(id: Long): ApplicationDto? {
        val application = getById(id) ?: return null
        if (!isStillPending(application)) {
            throw IllegalArgumentException("Application already sent")
        }
        sendApplicationToAllu(application, pendingOnClient = false)
        return applicationToDto(repo.save(application))
    }

    private fun getById(id: Long): AlluApplication? {
        return repo.findOneByIdAndUserId(id, currentUserId())
    }

    private fun isStillPending(application: AlluApplication): Boolean {
        // If there's no alluid then we haven't successfully sent this to ALLU yet (at all)
        val id = application.alluid ?: return true

        // If we already have an id but there's no status available then the application is still
        // pendingOnClient because ALLU doesn't report status events for applications that are in
        // the "meta state" pendingOnClient
        val currentStatus = cableReportService.getCurrentStatus(id) ?: return true

        // We've already sent this application with pendingOnClient: false
        return currentStatus == ApplicationStatus.PENDING
    }

    private fun trySendingPendingApplicationToAllu(application: AlluApplication) {
        try {
            sendApplicationToAllu(application, pendingOnClient = true)
        } catch (ignore: Exception) {
            // Just ignore it, maybe applicationData is missing something required which is fine
        }
    }

    private fun sendApplicationToAllu(application: AlluApplication, pendingOnClient: Boolean) {
        when (application.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                sendCableReport(application, pendingOnClient)
            }
        }
    }

    private fun sendCableReport(application: AlluApplication, pendingOnClient: Boolean) {
        val cableReportApplication: CableReportApplication =
            OBJECT_MAPPER.treeToValue(application.applicationData)!!
        if (cableReportApplication.pendingOnClient != pendingOnClient) {
            cableReportApplication.pendingOnClient = pendingOnClient
            application.applicationData = OBJECT_MAPPER.valueToTree(cableReportApplication)
        }

        withDisclosureLogging(cableReportApplication) {
            val alluId = application.alluid
            if (alluId == null) {
                application.alluid = cableReportService.create(cableReportApplication)
            } else {
                cableReportService.update(alluId, cableReportApplication)
            }
        }
    }

    /**
     * Save disclosure logs for the personal information inside the application. Determine the
     * status of the operation from whether there were exceptions or not. Don't save logging
     * failures, since personal data was not yet disclosed.
     */
    private fun withDisclosureLogging(
        cableReportApplication: CableReportApplication,
        f: (CableReportApplication) -> Unit
    ) {
        try {
            f(cableReportApplication)
        } catch (e: AlluLoginException) {
            // Since the login failed we didn't send the application itself, so logging not needed.
            throw e
        } catch (e: Throwable) {
            // There was an exception outside logging, so there was at least an attempt to send the
            // application to Allu. Allu might have read it and rejected it, so we should log this
            // as a disclosure event.
            disclosureLogService.saveDisclosureLogsForAllu(
                cableReportApplication,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
            throw e
        }
        // There were no exceptions, so log this as a successful disclosure.
        disclosureLogService.saveDisclosureLogsForAllu(cableReportApplication, Status.SUCCESS)
    }
}

@Repository
interface ApplicationRepository : JpaRepository<AlluApplication, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String): AlluApplication?
    fun getAllByUserId(userId: String): List<AlluApplication>
}
