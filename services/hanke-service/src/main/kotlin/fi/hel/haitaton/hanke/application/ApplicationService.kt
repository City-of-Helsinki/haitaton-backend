package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeMapper.domainFrom
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationPdfService
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.contactPersonEmails
import fi.hel.haitaton.hanke.email.ApplicationNotificationData
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.permissions.HankeKayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.validation.ApplicationDataValidator.ensureValidForSend
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"
const val ALLU_USER_CANCELLATION_MSG = "Käyttäjä perui hakemuksen Haitattomassa."
const val ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG =
    "Haitaton ei saanut lisättyä hakemuksen liitteitä. Hakemus peruttu."

open class ApplicationService(
    private val applicationRepository: ApplicationRepository,
    private val alluStatusRepository: AlluStatusRepository,
    private val cableReportService: CableReportService,
    private val disclosureLogService: DisclosureLogService,
    private val applicationLoggingService: ApplicationLoggingService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val emailSenderService: EmailSenderService,
    private val attachmentService: ApplicationAttachmentService,
    private val geometriatDao: GeometriatDao,
    private val permissionService: PermissionService,
    private val hankeRepository: HankeRepository,
    private val hankeLoggingService: HankeLoggingService,
    private val featureFlags: FeatureFlags,
) {
    @Transactional(readOnly = true)
    open fun getAllApplicationsForUser(userId: String): List<Application> {
        val hankeIds =
            permissionService.getAllowedHankeIds(userId = userId, permission = PermissionCode.VIEW)
        return hankeRepository
            .findAllById(hankeIds)
            .flatMap { it.hakemukset }
            .map { it.toApplication() }
    }

    @Transactional(readOnly = true)
    open fun getAllApplicationsCreatedByUser(userId: String): List<Application> {
        return applicationRepository.getAllByUserId(userId).map { it.toApplication() }
    }

    @Transactional(readOnly = true)
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

        if (!hanke.generated) {
            application.applicationData.areas?.let { areas ->
                checkApplicationAreasInsideHankealue(hanke.id, areas) { applicationArea ->
                    "Application geometry doesn't match any hankealue when creating a new application for user $userId, " +
                        "hankeId = ${hanke.id}, application geometry = ${applicationArea.geometry.toJsonString()}"
                }
            }
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
        val previousApplication = application.toApplication()
        logger.info("Updating application id=$id, alluid=${application.alluid}")

        if (previousApplication.applicationData == newApplicationData) {
            logger.info {
                "Not updating unchanged application data. id=$id, " +
                    "alluid=${application.alluid}, identifier=${application.applicationIdentifier}"
            }
            return previousApplication
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

        val hanke = application.hanke
        if (!hanke.generated) {
            newApplicationData.areas?.let { areas ->
                checkApplicationAreasInsideHankealue(hanke.id, areas) { applicationArea ->
                    "Application geometry doesn't match any hankealue when updating application for user $userId, " +
                        "hankeId = ${hanke.id}, applicationId = ${application.id}, " +
                        "application geometry = ${applicationArea.geometry.toJsonString()}"
                }
            }
        }

        if (!isStillPending(application.alluid, application.alluStatus)) {
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }

        // Don't change a draft to a non-draft or vice-versa with the update method.
        val pending = application.applicationData.pendingOnClient
        application.applicationData = newApplicationData.copy(pendingOnClient = pending)

        val saved = applicationRepository.save(application)

        // Update the application in Allu, if it's been already uploaded
        if (saved.alluid != null) {
            updateApplicationInAllu(saved)
            provideAccessOnAlluUpdate(saved, previousApplication.applicationData, userId, hanke)
        }

        return saved.toApplication().also {
            logger.info("Updated application id=${it.id}, alluid=${it.alluid}")
            applicationLoggingService.logUpdate(previousApplication, it, userId)
        }
    }

    @Transactional
    open fun sendApplication(id: Long, userId: String): Application {
        val application = getById(id)

        val hanke = application.hanke
        if (!hanke.generated) {
            application.applicationData.areas?.let { areas ->
                checkApplicationAreasInsideHankealue(hanke.id, areas) { applicationArea ->
                    "Application geometry doesn't match any hankealue when sending application for user $userId, " +
                        "hankeId = ${hanke.id}, applicationId = ${application.id}, " +
                        "application geometry = ${applicationArea.geometry.toJsonString()}"
                }
            }
        }

        logger.info("Sending application id=$id, alluid=${application.alluid}")
        if (!isStillPending(application.alluid, application.alluStatus)) {
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

        logger.info { "Sending the application to Allu. id=$id" }
        application.alluid = sendApplicationToAllu(application)

        logger.info {
            "Application sent, fetching application identifier and status. id=$id, alluid=${application.alluid}."
        }
        getApplicationInformationFromAllu(application.alluid!!)?.let { response ->
            application.applicationIdentifier = response.applicationId
            application.alluStatus = response.status
            initAccessOnInitialSend(application, hanke, userId)
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
        val status = alluStatusRepository.getReferenceById(1)
        status.historyLastUpdated = updateTime
        alluStatusRepository.save(status)
    }

    /**
     * Deletes an application. Cancels the application in Allu if it's still pending. Refuses to
     * delete, if the application is in Allu, and it's beyond the pending status.
     */
    @Transactional
    open fun delete(applicationId: Long, userId: String) =
        with(getById(applicationId)) { cancelAndDelete(this, userId) }

    /**
     * Deletes an application. Cancels the application in Allu if it's still pending. Refuses to
     * delete, if the application is in Allu, and it's beyond the pending status.
     *
     * Furthermore, if the owning Hanke is generated and has no more applications, also deletes the
     * Hanke.
     */
    @Transactional
    open fun deleteWithOrphanGeneratedHankeRemoval(
        applicationId: Long,
        userId: String
    ): ApplicationDeletionResultDto {
        val application = getById(applicationId)
        val hanke = application.hanke

        cancelAndDelete(application, userId)

        if (hanke.generated && hanke.hakemukset.size == 1) {
            logger.info {
                "Application ${application.id} was the only one of a generated Hanke, removing Hanke ${hanke.hankeTunnus}."
            }
            hankeRepository.delete(hanke)
            val hankeDomain = domainFrom(hanke, geometryMapFrom(hanke))
            hankeLoggingService.logDelete(hankeDomain, userId)

            return ApplicationDeletionResultDto(hankeDeleted = true)
        }

        return ApplicationDeletionResultDto(hankeDeleted = false)
    }

    @Transactional(readOnly = true)
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

    /**
     * An application is being processed in Allu if status it is NOT pending anymore. Pending status
     * needs verification from Allu. A post-pending status can never go back to pending.
     */
    open fun isStillPending(alluId: Int?, alluStatus: ApplicationStatus?): Boolean =
        when (alluStatus) {
            null,
            PENDING,
            PENDING_CLIENT -> isStillPendingInAllu(alluId)
            else -> false
        }

    private fun isStillPendingInAllu(alluid: Int?): Boolean {
        // If there's no alluid then we haven't successfully sent this to ALLU yet (at all)
        alluid ?: return true

        val currentStatus = cableReportService.getApplicationInformation(alluid).status

        return currentStatus in listOf(PENDING, PENDING_CLIENT)
    }

    private fun cancelAndDelete(application: ApplicationEntity, userId: String) =
        with(application) {
            val alluId = alluid
            if (alluId == null) {
                logger.info { "Application not sent to Allu yet, simply deleting it. id=$id" }
            } else {
                logger.info {
                    "Application is sent to Allu, trying to cancel it before deleting. id=$id alluid=$alluid"
                }
                cancelApplication(id, alluId, alluStatus)
            }

            logger.info { "Deleting application, id=$id, alluid=$alluid userid=$userId" }
            applicationRepository.delete(this)
            applicationLoggingService.logDelete(toApplication(), userId)
            logger.info { "Application deleted, id=$id, alluid=$alluid userid=$userId" }
        }

    /** Creates access for all application contacts, excluding the current user. */
    private fun initAccessOnInitialSend(
        application: ApplicationEntity,
        hanke: HankeEntity,
        currentUserId: String,
    ) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            logger.info { "Feature ${Feature.USER_MANAGEMENT} disabled. No tokens created." }
            return
        }

        val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, currentUserId)
        val contactEmails =
            application.applicationData.contactPersonEmails(omit = kayttaja?.sahkoposti)

        provideAccess(
            application = application,
            hanke = hanke,
            currentKayttaja = kayttaja,
            currentUserId = currentUserId,
            emailRecipients = contactEmails
        )
    }

    /** Creates access for new application contacts. Email address used for comparison. */
    private fun provideAccessOnAlluUpdate(
        application: ApplicationEntity,
        previousData: ApplicationData,
        currentUserId: String,
        hanke: HankeEntity
    ) {
        if (featureFlags.isDisabled(Feature.USER_MANAGEMENT)) {
            logger.info { "Feature ${Feature.USER_MANAGEMENT} disabled. No tokens created." }
            return
        }

        val kayttaja = hankeKayttajaService.getKayttajaByUserId(hanke.id, currentUserId)

        val previous = previousData.contactPersonEmails()
        val updated = application.applicationData.contactPersonEmails(omit = kayttaja?.sahkoposti)
        val newEmails = updated.subtract(previous)

        provideAccess(
            application = application,
            hanke = hanke,
            currentKayttaja = kayttaja,
            currentUserId = currentUserId,
            emailRecipients = newEmails
        )
    }

    private fun provideAccess(
        application: ApplicationEntity,
        hanke: HankeEntity,
        currentKayttaja: HankeKayttajaEntity?,
        currentUserId: String,
        emailRecipients: Set<String>,
    ) {
        hankeKayttajaService.saveNewTokensFromApplication(
            application = application,
            hankeId = hanke.id,
            hankeTunnus = hanke.hankeTunnus,
            hankeNimi = hanke.nimi,
            currentUserId = currentUserId,
            currentKayttaja = currentKayttaja
        )

        emailRecipients.forEach { email ->
            notifyOnApplication(
                hanke.hankeTunnus,
                application.applicationIdentifier!!,
                application.applicationType,
                currentKayttaja,
                email,
            )
        }
    }

    private fun notifyOnApplication(
        hankeTunnus: String,
        applicationIdentifier: String,
        applicationType: ApplicationType,
        currentKayttaja: HankeKayttajaEntity?,
        recipientEmail: String,
    ) {
        logger.info { "Sending Application notification." }

        if (currentKayttaja == null) {
            logger.warn { "Sending kayttaja is null, will not send application notification." }
            return
        }

        emailSenderService.sendApplicationNotificationEmail(
            ApplicationNotificationData(
                senderName = currentKayttaja.nimi,
                senderEmail = currentKayttaja.sahkoposti,
                recipientEmail = recipientEmail,
                hankeTunnus = hankeTunnus,
                applicationIdentifier = applicationIdentifier,
                applicationType = applicationType,
            )
        )
    }

    /** Cancel an application that's been sent to Allu. */
    private fun cancelApplication(id: Long?, alluId: Int, alluStatus: ApplicationStatus?) {
        if (isStillPending(alluId, alluStatus)) {
            logger.info {
                "Application is still pending, trying to cancel it. id=$id alluid=${alluId}"
            }
            cableReportService.cancel(alluId)
            cableReportService.sendSystemComment(alluId, ALLU_USER_CANCELLATION_MSG)
            logger.info { "Application canceled, proceeding to delete it. id=$id alluid=${alluId}" }
        } else {
            throw ApplicationAlreadyProcessingException(id, alluId)
        }
    }

    private fun validateGeometry(
        newApplicationData: ApplicationData,
        customMessageOnFailure: (GeometriatDao.InvalidDetail) -> String
    ) {
        val areas = newApplicationData.areas
        if (areas != null) {
            geometriatDao.validateGeometriat(areas.map { it.geometry })?.let {
                throw ApplicationGeometryException(customMessageOnFailure(it))
            }
        }
    }

    private fun checkApplicationAreasInsideHankealue(
        hankeId: Int,
        areas: List<ApplicationArea>,
        customMessageOnFailure: (ApplicationArea) -> String
    ) {
        areas.forEach { area ->
            if (!geometriatDao.isInsideHankeAlueet(hankeId, area.geometry))
                throw ApplicationGeometryNotInsideHankeException(customMessageOnFailure(area))
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
            .forEach { handleApplicationEvent(application, it) }
        applicationRepository.save(application)
    }

    private fun handleApplicationEvent(
        application: ApplicationEntity,
        event: ApplicationStatusEvent
    ) {
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
        if (event.newStatus == ApplicationStatus.DECISION) {
            sendDecisionReadyEmails(application, event.applicationIdentifier)
        }
    }

    private fun sendDecisionReadyEmails(
        application: ApplicationEntity,
        applicationIdentifier: String
    ) {
        val receivers =
            application.applicationData
                .customersWithContacts()
                .flatMap { it.contacts }
                .filter { it.orderer }

        if (receivers.isEmpty()) {
            logger.error {
                "No receivers found for decision ready email, not sending any." +
                    "applicationId=${application.id}, applicationIdentifier=${applicationIdentifier}"
            }
            return
        }
        logger.info { "Sending application ready emails to ${receivers.size} receivers" }

        receivers.forEach {
            sendDecisionReadyEmail(it.email, applicationIdentifier, application.id)
        }
    }

    private fun sendDecisionReadyEmail(
        email: String?,
        applicationIdentifier: String,
        applicationId: Long?,
    ) {
        if (email == null) {
            logger.error {
                "Can't send decision ready email, because contact email is null. " +
                    "applicationId=$applicationId, applicationIdentifier=${applicationIdentifier}"
            }
            return
        }

        emailSenderService.sendJohtoselvitysCompleteEmail(
            email,
            applicationId,
            applicationIdentifier
        )
    }

    private fun getById(id: Long): ApplicationEntity {
        return applicationRepository.findOneById(id) ?: throw ApplicationNotFoundException(id)
    }

    private fun getApplicationInformationFromAllu(alluid: Int): AlluApplicationResponse? {
        return try {
            cableReportService.getApplicationInformation(alluid)
        } catch (e: Exception) {
            logger.error(e) { "Exception while getting application information." }
            null
        }
    }

    private fun sendApplicationToAllu(entity: ApplicationEntity): Int {
        return if (entity.alluid == null) {
            createApplicationInAllu(entity)
        } else {
            updateApplicationInAllu(entity)
        }
    }

    private fun updateApplicationInAllu(entity: ApplicationEntity): Int {
        ensureValidForSend(entity.applicationData)
        val alluId = entity.alluid ?: throw ApplicationArgumentException("AlluId null in update.")

        logger.info { "Uploading updated application with alluId $alluId" }

        when (val data = entity.applicationData) {
            is CableReportApplicationData ->
                updateCableReportInAllu(entity.id!!, alluId, entity.hanke.hankeTunnus, data)
        }

        return alluId
    }

    /** Creates new application in Allu. All attachments are sent after creation. */
    private fun createApplicationInAllu(entity: ApplicationEntity): Int {
        ensureValidForSend(entity.applicationData)
        val alluId =
            when (val data = entity.applicationData) {
                is CableReportApplicationData ->
                    createCableReportToAllu(entity.id!!, entity.hanke.hankeTunnus, data)
            }
        try {
            attachmentService.sendInitialAttachments(alluId, entity.id)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while sending the initial attachments. Canceling the application. " +
                    "id=${entity.id}, alluid=$alluId"
            }
            cableReportService.cancel(alluId)
            cableReportService.sendSystemComment(alluId, ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG)
            throw e
        }

        return alluId
    }

    private fun createCableReportToAllu(
        applicationId: Long,
        hankeTunnus: String,
        cableReport: CableReportApplicationData
    ): Int {
        val alluData = cableReport.toAlluData(hankeTunnus)

        return withFormDataPdfUploading(cableReport) {
            withDisclosureLogging(applicationId, cableReport) {
                cableReportService.create(alluData)
            }
        }
    }

    private fun updateCableReportInAllu(
        applicationId: Long,
        alluId: Int,
        hankeTunnus: String,
        cableReport: CableReportApplicationData
    ) {
        val alluData = cableReport.toAlluData(hankeTunnus)

        withFormDataPdfUploading(cableReport) {
            withDisclosureLogging(applicationId, cableReport) {
                cableReportService.update(alluId, alluData)
            }
            alluId
        }
    }

    /**
     * Transform the data in the Haitaton application form to a PDF and send it to Allu as an
     * attachment.
     *
     * Form the PDF file before running the action in allu (create or update). This way, if there's
     * a problem with creating the PDF, the application is not added or updated.
     *
     * Keep going even if the PDF upload fails for some reason. The application has already been
     * created/updated in Allu, so it's too late to stop on an exception. Failing to upload the PDF
     * is also not reason enough to cancel the application in Allu.
     *
     * @param alluAction The action to perform in Allu. Must return the application's Allu ID.
     */
    private fun withFormDataPdfUploading(
        cableReport: CableReportApplicationData,
        alluAction: () -> Int
    ): Int {
        val formAttachment = getApplicationDataAsPdf(cableReport)

        val alluId = alluAction()

        try {
            cableReportService.addAttachment(alluId, formAttachment)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while uploading form data PDF attachment. Continuing anyway. alluid=$alluId"
            }
        }

        return alluId
    }

    private fun getApplicationDataAsPdf(data: CableReportApplicationData): Attachment {
        logger.info { "Creating a PDF from the application data for data attachment." }
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
        logger.info { "Created the PDF for data attachment." }
        return Attachment(attachmentMetadata, pdfData)
    }

    /**
     * Save disclosure logs for the personal information inside the application. Determine the
     * status of the operation from whether there were exceptions or not. Don't save logging
     * failures, since personal data was not yet disclosed.
     */
    private fun <T> withDisclosureLogging(
        applicationId: Long,
        cableReportApplicationData: CableReportApplicationData,
        f: () -> T,
    ): T {
        try {
            val result = f()
            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
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
                applicationId,
                cableReportApplicationData,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
            throw e
        }
    }

    /** Map by area geometry id to area geometry data. */
    private fun geometryMapFrom(hanke: HankeEntity) =
        hanke.listOfHankeAlueet
            .mapNotNull { it.geometriat }
            .associateBy({ it }, { geometriatDao.retrieveGeometriat(it) })
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

class ApplicationGeometryNotInsideHankeException(message: String) : RuntimeException(message)

class ApplicationDecisionNotFoundException(message: String) : RuntimeException(message)

class ApplicationArgumentException(message: String) : RuntimeException(message)
