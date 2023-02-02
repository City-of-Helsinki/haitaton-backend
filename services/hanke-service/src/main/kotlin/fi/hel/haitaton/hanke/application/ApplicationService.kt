package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.Status
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import mu.KotlinLogging
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"

open class ApplicationService(
    private val repo: ApplicationRepository,
    private val alluStatusRepository: AlluStatusRepository,
    private val cableReportService: CableReportService,
    private val disclosureLogService: DisclosureLogService,
    private val applicationLoggingService: ApplicationLoggingService,
    private val geometriatDao: GeometriatDao,
) {
    open fun getAllApplicationsForUser(userId: String): List<Application> {
        return repo.getAllByUserId(userId).map { it.toApplication() }
    }

    open fun getApplicationById(id: Long, userId: String): Application =
        getById(id, userId).toApplication()

    @Transactional
    open fun create(application: Application, userId: String): Application {
        logger.info("Creating a new application for user $userId")

        geometriatDao.validateGeometria(application.applicationData.geometry)?.let {
            throw ApplicationGeometryException(
                "Invalid geometry received when creating a new application for user $userId, reason = ${it.reason}, location = ${it.location}"
            )
        }

        val applicationEntity =
            ApplicationEntity(
                id = null,
                alluid = null,
                alluStatus = null,
                applicationIdentifier = null,
                userId = userId,
                applicationType = application.applicationType,
                // The application is still a draft in Haitaton until the customer explicitly sends
                // it to Allu
                applicationData = application.applicationData.copy(pendingOnClient = true)
            )

        val savedApplication = repo.save(applicationEntity).toApplication()
        logger.info { "Created a new application with id ${savedApplication.id} for user $userId" }
        applicationLoggingService.logCreate(savedApplication, userId)
        return savedApplication
    }

    @Transactional
    open fun updateApplicationData(
        id: Long,
        newApplicationData: ApplicationData,
        userId: String,
    ): Application {
        val application = getById(id, userId)
        val applicationBefore = application.toApplication()
        logger.info("Updating application id=$id, alluid=${application.alluid}")

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

        geometriatDao.validateGeometria(newApplicationData.geometry)?.let {
            throw ApplicationGeometryException(
                "Invalid geometry received when updating application for user $userId, id=${application.id}, alluid=${application.alluid}, reason = ${it.reason}, location = ${it.location}"
            )
        }

        if (!isStillPending(application)) {
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }

        // Don't change a draft to a non-draft or vice-versa with the update method.
        application.applicationData =
            newApplicationData.copy(pendingOnClient = application.applicationData.pendingOnClient)

        // Update the application in Allu, if it's been already uploaded
        if (application.alluid != null) {
            updateApplicationInAllu(application.alluid!!, application.applicationData)
        }

        val updatedApplication = repo.save(application).toApplication()
        logger.info("Updated application id=$id, alluid=${updatedApplication.alluid}")
        applicationLoggingService.logUpdate(applicationBefore, updatedApplication, userId)
        return updatedApplication
    }

    open fun sendApplication(id: Long, userId: String): Application {
        val application = getById(id, userId)
        logger.info("Sending application id=$id, alluid=${application.alluid}")
        if (!isStillPending(application)) {
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }
        // The application should no longer be a draft
        application.applicationData = application.applicationData.copy(pendingOnClient = false)
        application.alluid = sendApplicationToAllu(application.alluid, application.applicationData)
        getApplicationInformationFromAllu(application.alluid!!)?.let {
            application.applicationIdentifier = it.applicationId
            application.alluStatus = it.status
        }
        logger.info("Sent application id=$id, alluid=${application.alluid}")
        // Save only if sendApplicationToAllu didn't throw an exception
        return repo.save(application).toApplication()
    }

    private fun getApplicationInformationFromAllu(alluid: Int): AlluApplicationResponse? {
        return try {
            cableReportService.getApplicationInformation(alluid)
        } catch (e: Exception) {
            logger.error(e) { "Exception while getting application information." }
            null
        }
    }

    @Transactional
    open fun handleApplicationUpdates(
        applicationHistories: List<ApplicationHistory>,
        updateTime: OffsetDateTime
    ) {
        applicationHistories.forEach { handleApplicationUpdate(it) }
        val status = alluStatusRepository.getOne(1)
        status.historyLastUpdated = updateTime
        alluStatusRepository.save(status)
    }

    private fun handleApplicationUpdate(applicationHistory: ApplicationHistory) {
        val application = repo.getOneByAlluid(applicationHistory.applicationId)
        if (application == null) {
            logger.error {
                "Allu had events for an application we don't have anymore. alluid=${applicationHistory.applicationId}"
            }
            return
        }
        applicationHistory.events
            .sortedBy { it.eventTime }
            .forEach { event ->
                application.alluStatus = event.newStatus
                application.applicationIdentifier = event.applicationIdentifier
                logger.info {
                    "Updating application with new status, " +
                        "id=${application.id}, " +
                        "alluid=${application.alluid}, " +
                        "application identifier=${application.applicationIdentifier}, " +
                        "new status=${application.alluStatus}, " +
                        "event time=${event.eventTime}"
                }
            }
        repo.save(application)
    }

    private fun getById(id: Long, userId: String): ApplicationEntity {
        return repo.findOneByIdAndUserId(id, userId) ?: throw ApplicationNotFoundException(id)
    }

    private fun isStillPending(application: ApplicationEntity): Boolean {
        // If there's no alluid then we haven't successfully sent this to ALLU yet (at all)
        val alluid = application.alluid ?: return true

        val currentStatus = cableReportService.getApplicationInformation(alluid).status

        return currentStatus in listOf(ApplicationStatus.PENDING, ApplicationStatus.PENDING_CLIENT)
    }

    private fun sendApplicationToAllu(alluid: Int?, applicationData: ApplicationData): Int {
        return if (alluid == null) {
            createApplicationInAllu(applicationData)
        } else {
            updateApplicationInAllu(alluid, applicationData)
            alluid
        }
    }

    private fun updateApplicationInAllu(alluid: Int, applicationData: ApplicationData) {
        when (applicationData) {
            is CableReportApplicationData -> updateCableReportInAllu(alluid, applicationData)
        }
    }

    private fun createApplicationInAllu(applicationData: ApplicationData): Int {
        return when (applicationData) {
            is CableReportApplicationData -> createCableReportToAllu(applicationData)
        }
    }

    private fun createCableReportToAllu(
        cableReportApplicationData: CableReportApplicationData
    ): Int {
        val alluData = cableReportApplicationData.toAlluData()
        return withDisclosureLogging(cableReportApplicationData) {
            cableReportService.create(alluData)
        }
    }

    private fun updateCableReportInAllu(
        alluId: Int,
        cableReportApplicationData: CableReportApplicationData
    ) {
        val alluData = cableReportApplicationData.toAlluData()
        withDisclosureLogging(cableReportApplicationData) {
            cableReportService.update(alluId, alluData)
        }
    }

    /**
     * Save disclosure logs for the personal information inside the application. Determine the
     * status of the operation from whether there were exceptions or not. Don't save logging
     * failures, since personal data was not yet disclosed.
     */
    private fun <T> withDisclosureLogging(
        cableReportApplicationData: CableReportApplicationData,
        f: (CableReportApplicationData) -> T,
    ): T {
        try {
            val result = f(cableReportApplicationData)
            disclosureLogService.saveDisclosureLogsForAllu(
                cableReportApplicationData,
                Status.SUCCESS
            )
            return result
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
    }
}

class IncompatibleApplicationException(
    applicationId: Long,
    oldApplicationClass: KClass<out ApplicationData>,
    newApplicationClass: KClass<out ApplicationData>,
) :
    RuntimeException(
        "Tried to update application $applicationId of type $oldApplicationClass with $newApplicationClass"
    )

class ApplicationNotFoundException(id: Long) :
    RuntimeException("Application not found with id $id")

class ApplicationAlreadyProcessingException(id: Long?, alluid: Int?) :
    RuntimeException("Application is no longer pending in Allu, id=$id, alluid=$alluid")

class ApplicationGeometryException(message: String) : RuntimeException(message)

@Repository
interface ApplicationRepository : JpaRepository<ApplicationEntity, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String): ApplicationEntity?

    fun getAllByUserId(userId: String): List<ApplicationEntity>

    @Query("select alluid from ApplicationEntity where alluid is not null")
    fun getAllAlluIds(): List<Int>

    fun getOneByAlluid(alluid: Int): ApplicationEntity?
}
