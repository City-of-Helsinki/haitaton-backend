package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationPdfService
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import mu.KotlinLogging
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.http.MediaType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"

open class ApplicationService(
    private val applicationRepository: ApplicationRepository,
    private val alluStatusRepository: AlluStatusRepository,
    private val cableReportService: CableReportService,
    private val disclosureLogService: DisclosureLogService,
    private val applicationLoggingService: ApplicationLoggingService,
    private val geometriatDao: GeometriatDao,
    private val permissionService: PermissionService,
    private val hankeRepository: HankeRepository,
) {
    @Transactional
    open fun getAllApplicationsForUser(userId: String): List<Application> {
        val result = applicationRepository.getAllByUserId(userId).toMutableSet()
        val hankkeet =
            permissionService.getAllowedHankeIds(userId = userId, permission = PermissionCode.VIEW)
        hankkeet.forEach { result.addAll(hankeRepository.getOne(it).hakemukset) }
        return result.map { it.toApplication() }
    }

    open fun getApplicationById(id: Long): Application = getById(id).toApplication()

    @Transactional
    open fun create(application: Application, userId: String): Application {
        logger.info("Creating a new application for user $userId")

        validateGeometry(application.applicationData) { validationError ->
            "Invalid geometry received when creating a new application for user $userId, reason = ${validationError.reason}, location = ${validationError.location}"
        }

        val hanke =
            hankeRepository.findByHankeTunnus(application.hankeTunnus)
                ?: throw HankeNotFoundException(application.hankeTunnus)

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
                applicationData = application.applicationData.copy(pendingOnClient = true),
                hanke = hanke,
            )

        val savedApplicationEntity = applicationRepository.save(applicationEntity)
        hanke.hakemukset.add(savedApplicationEntity)
        val savedApplication = savedApplicationEntity.toApplication()
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
        val application = getById(id)
        val applicationBefore = application.toApplication()
        logger.info("Updating application id=$id, alluid=${application.alluid}")

        if (applicationBefore.applicationData == newApplicationData) {
            logger.info {
                "Not updating unchanged application data. id=$id, " +
                    "alluid=${application.alluid}, identifier=${application.applicationIdentifier}"
            }
            return applicationBefore
        }

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

        validateGeometry(newApplicationData) { validationError ->
            "Invalid geometry received when updating application for user $userId, id=${application.id}, alluid=${application.alluid}, reason = ${validationError.reason}, location = ${validationError.location}"
        }

        if (!isStillPending(application.alluid)) {
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }

        // Don't change a draft to a non-draft or vice-versa with the update method.
        application.applicationData =
            newApplicationData.copy(pendingOnClient = application.applicationData.pendingOnClient)

        // Update the application in Allu, if it's been already uploaded
        if (application.alluid != null) {
            updateApplicationInAllu(application.alluid!!, application.applicationData)
        }

        val updatedApplication = applicationRepository.save(application).toApplication()
        logger.info("Updated application id=$id, alluid=${updatedApplication.alluid}")
        applicationLoggingService.logUpdate(applicationBefore, updatedApplication, userId)
        return updatedApplication
    }

    open fun sendApplication(id: Long, userId: String): Application {
        val application = getById(id)

        logger.info("Sending application id=$id, alluid=${application.alluid}")
        if (!isStillPending(application.alluid)) {
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }

        if (application.alluid != null && !application.applicationData.pendingOnClient) {
            // Re-sending is done with update, this should only be used for initial send to Allu.
            logger.info {
                "Not re-sending application that was already sent. id=$id, " +
                    "alluid=${application.alluid}, identifier=${application.applicationIdentifier}"
            }
            return application.toApplication()
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
        return applicationRepository.save(application).toApplication()
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

    /**
     * Deletes an application. Cancels the application in Allu if it's still pending. Refuses to
     * delete, if the application is in Allu, and it's beyond the pending status.
     */
    @Transactional
    open fun delete(id: Long, userId: String) {
        val application = getById(id)
        val alluid = application.alluid
        logger.info { "Deleting application, id=$id, alluid=$alluid userid=$userId" }

        if (alluid == null) {
            logger.info { "Application not sent to Allu yet, simply deleting it. id=$id" }
        } else {
            logger.info {
                "Application sent to Allu yet, trying to cancel it before deleting. id=$id alluid=$alluid"
            }
            cancelApplication(alluid, application.id)
        }
        applicationRepository.deleteById(id)
        logger.info { "Application deleted, id=$id, alluid=$alluid userid=$userId" }
        applicationLoggingService.logDelete(application.toApplication(), userId)
    }

    open fun downloadDecision(applicationId: Long, userId: String): Pair<String, ByteArray> {
        val application = getApplicationById(applicationId)
        val alluid =
            application.alluid
                ?: throw ApplicationDecisionNotFoundException(
                    "Application not in Allu, so it doesn't have a decision. id=${application.id}"
                )
        val filename = application.applicationIdentifier ?: "paatos"
        val pdfBytes = cableReportService.getDecisionPdf(alluid)
        return Pair(filename, pdfBytes)
    }

    /** Cancel an application that's been sent to Allu. */
    private fun cancelApplication(alluid: Int, id: Long?) {
        if (isStillPending(alluid)) {
            logger.info {
                "Application is still pending, trying to cancel it. id=$id alluid=${alluid}"
            }
            cableReportService.cancel(alluid)
            logger.info { "Application canceled, proceeding to delete it. id=$id alluid=${alluid}" }
        } else {
            throw ApplicationAlreadyProcessingException(id, alluid)
        }
    }

    private fun validateGeometry(
        newApplicationData: ApplicationData,
        customMessageOnFailure: (GeometriatDao.InvalidDetail) -> String
    ) {
        val areas = newApplicationData.areas
        val geometry = newApplicationData.geometry
        if (areas != null) {
            geometriatDao.validateGeometriat(areas.map { it.geometry })?.let {
                throw ApplicationGeometryException(customMessageOnFailure(it))
            }
        } else if (geometry != null) {
            geometriatDao.validateGeometria(newApplicationData.geometry!!)?.let {
                throw ApplicationGeometryException(customMessageOnFailure(it))
            }
        }
    }

    private fun handleApplicationUpdate(applicationHistory: ApplicationHistory) {
        val application = applicationRepository.getOneByAlluid(applicationHistory.applicationId)
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
        applicationRepository.save(application)
    }

    private fun getById(id: Long): ApplicationEntity {
        return applicationRepository.findOneById(id) ?: throw ApplicationNotFoundException(id)
    }

    private fun isStillPending(alluid: Int?): Boolean {
        // If there's no alluid then we haven't successfully sent this to ALLU yet (at all)
        alluid ?: return true

        val currentStatus = cableReportService.getApplicationInformation(alluid).status

        return currentStatus in listOf(ApplicationStatus.PENDING, ApplicationStatus.PENDING_CLIENT)
    }

    private fun getApplicationInformationFromAllu(alluid: Int): AlluApplicationResponse? {
        return try {
            cableReportService.getApplicationInformation(alluid)
        } catch (e: Exception) {
            logger.error(e) { "Exception while getting application information." }
            null
        }
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
        val attachment = getApplicationDataAsPdf(cableReportApplicationData)

        val alluid =
            withDisclosureLogging(cableReportApplicationData) {
                cableReportService.create(alluData)
            }

        cableReportService.addAttachment(alluid, attachment)
        return alluid
    }

    private fun getApplicationDataAsPdf(data: CableReportApplicationData): Attachment {
        val totalArea =
            geometriatDao.calculateCombinedArea(data.areas?.map { it.geometry } ?: listOf())
        val areas = data.areas?.map { geometriatDao.calculateArea(it.geometry) } ?: listOf()
        val attachmentMetadata =
            AttachmentMetadata(
                id = null,
                mimeType = MediaType.APPLICATION_PDF_VALUE,
                name = "haitaton-form-data.pdf",
                description = "Original form data from Haitaton, dated ${LocalDateTime.now()}.",
            )
        val pdfData = ApplicationPdfService.createPdf(data, totalArea, areas)
        return Attachment(attachmentMetadata, pdfData)
    }

    private fun updateCableReportInAllu(
        alluId: Int,
        cableReportApplicationData: CableReportApplicationData
    ) {
        val alluData = cableReportApplicationData.toAlluData()
        val attachment = getApplicationDataAsPdf(cableReportApplicationData)

        withDisclosureLogging(cableReportApplicationData) {
            cableReportService.update(alluId, alluData)
        }

        cableReportService.addAttachment(alluId, attachment)
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

class ApplicationDecisionNotFoundException(message: String) : RuntimeException(message)

@Repository
interface ApplicationRepository : JpaRepository<ApplicationEntity, Long> {
    fun findOneByIdAndUserId(id: Long, userId: String): ApplicationEntity?

    fun findOneById(id: Long): ApplicationEntity?

    fun getAllByUserId(userId: String): List<ApplicationEntity>

    @Query("select alluid from ApplicationEntity where alluid is not null")
    fun getAllAlluIds(): List<Int>

    fun getOneByAlluid(alluid: Int): ApplicationEntity?
}
