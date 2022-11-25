package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.Status
import kotlin.reflect.KClass
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"

open class ApplicationService(
    private val repo: ApplicationRepository,
    private val cableReportService: CableReportService,
    private val disclosureLogService: DisclosureLogService,
    private val applicationLoggingService: ApplicationLoggingService,
) {
    open fun getAllApplicationsForUser(userId: String): List<Application> {
        return repo.getAllByUserId(userId).map { it.toApplication() }
    }

    open fun getApplicationById(id: Long, userId: String): Application =
        getById(id, userId).toApplication()

    @Transactional
    open fun create(application: Application, userId: String): Application {
        val applicationEntity =
            ApplicationEntity(
                id = null,
                alluid = null,
                userId = userId,
                applicationType = application.applicationType,
                applicationData = application.applicationData
            )
        trySendingPendingApplicationToAllu(applicationEntity)

        val savedApplication = repo.save(applicationEntity).toApplication()
        applicationLoggingService.logCreate(savedApplication, userId)
        return savedApplication
    }

    @Transactional
    open fun updateApplicationData(
        id: Long,
        newApplicationData: ApplicationData,
        userId: String
    ): Application {
        val application = getById(id, userId)
        val applicationBefore = application.toApplication()

        when (application.applicationData) {
            is CableReportApplicationData ->
                if (newApplicationData !is CableReportApplicationData) {
                    throw IncompatibleApplicationException(
                        id,
                        application.applicationData::class,
                        newApplicationData::class
                    )
                }
        }

        if (!isStillPending(application)) {
            throw IllegalArgumentException("Application already sent")
        }

        application.applicationData = newApplicationData
        trySendingPendingApplicationToAllu(application)

        val applicationAfter = repo.save(application).toApplication()
        applicationLoggingService.logUpdate(applicationBefore, applicationAfter, userId)
        return applicationAfter
    }

    open fun sendApplication(id: Long, userId: String): Application {
        val application = getById(id, userId)
        if (!isStillPending(application)) {
            throw IllegalArgumentException("Application already sent")
        }
        sendApplicationToAllu(application, pendingOnClient = false)
        return repo.save(application).toApplication()
    }

    private fun getById(id: Long, userId: String): ApplicationEntity {
        return repo.findOneByIdAndUserId(id, userId) ?: throw ApplicationNotFoundException(id)
    }

    private fun isStillPending(application: ApplicationEntity): Boolean {
        // If there's no alluid then we haven't successfully sent this to ALLU yet (at all)
        val id = application.alluid ?: return true

        // If we already have an id but there's no status available then the application is still
        // pendingOnClient because ALLU doesn't report status events for applications that are in
        // the "meta state" pendingOnClient
        val currentStatus = cableReportService.getCurrentStatus(id) ?: return true

        // We've already sent this application with pendingOnClient: false
        return currentStatus in listOf(ApplicationStatus.PENDING, ApplicationStatus.PENDING_CLIENT)
    }

    private fun trySendingPendingApplicationToAllu(application: ApplicationEntity) {
        try {
            sendApplicationToAllu(application, pendingOnClient = true)
        } catch (ignore: Exception) {
            // Just ignore it, maybe applicationData is missing something required which is fine
        }
    }

    private fun sendApplicationToAllu(application: ApplicationEntity, pendingOnClient: Boolean) {
        when (application.applicationData) {
            is CableReportApplicationData -> sendCableReport(application, pendingOnClient)
        }
    }

    private fun sendCableReport(application: ApplicationEntity, pendingOnClient: Boolean) {
        val cableReportApplicationData: CableReportApplicationData =
            application.applicationData as CableReportApplicationData
        if (cableReportApplicationData.pendingOnClient != pendingOnClient) {
            cableReportApplicationData.pendingOnClient = pendingOnClient
            application.applicationData = cableReportApplicationData
        }

        withDisclosureLogging(cableReportApplicationData) {
            val alluId = application.alluid
            if (alluId == null) {
                application.alluid = cableReportService.create(cableReportApplicationData)
            } else {
                cableReportService.update(alluId, cableReportApplicationData)
            }
        }
    }

    /**
     * Save disclosure logs for the personal information inside the application. Determine the
     * status of the operation from whether there were exceptions or not. Don't save logging
     * failures, since personal data was not yet disclosed.
     */
    private fun withDisclosureLogging(
        cableReportApplicationData: CableReportApplicationData,
        f: (CableReportApplicationData) -> Unit
    ) {
        try {
            f(cableReportApplicationData)
        } catch (e: AlluLoginException) {
            // Since the login failed we didn't send the application itself, so logging not needed.
            throw e
        } catch (e: Throwable) {
            // There was an exception outside logging, so there was at least an attempt to send the
            // application to Allu. Allu might have read it and rejected it, so we should log this
            // as a disclosure event.
            disclosureLogService.saveDisclosureLogsForAllu(
                cableReportApplicationData,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
            throw e
        }
        // There were no exceptions, so log this as a successful disclosure.
        disclosureLogService.saveDisclosureLogsForAllu(cableReportApplicationData, Status.SUCCESS)
    }
}

class IncompatibleApplicationException(
    applicationId: Long,
    oldApplicationClass: KClass<out ApplicationData>,
    newApplicationClass: KClass<out ApplicationData>
) :
    RuntimeException(
        "Tried to update application $applicationId of type $oldApplicationClass with $newApplicationClass"
    )

class ApplicationNotFoundException(id: Long) :
    RuntimeException("Application not found with id $id")

@Repository
interface ApplicationRepository : JpaRepository<ApplicationEntity, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String): ApplicationEntity?
    fun getAllByUserId(userId: String): List<ApplicationEntity>
}
