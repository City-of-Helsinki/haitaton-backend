package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeMapper.domainFrom
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.ApplicationPdfService
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.email.ApplicationNotificationData
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.validation.ApplicationDataValidator.ensureValidForSend
import java.time.LocalDateTime
import kotlin.reflect.KClass
import mu.KotlinLogging
import org.geojson.Polygon
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"
const val ALLU_USER_CANCELLATION_MSG = "Käyttäjä perui hakemuksen Haitattomassa."
const val ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG =
    "Haitaton ei saanut lisättyä hakemuksen liitteitä. Hakemus peruttu."

@Service
class ApplicationService(
    private val applicationRepository: ApplicationRepository,
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
    private val hankealueService: HankealueService,
) {
    @Transactional(readOnly = true)
    fun getAllApplicationsForUser(userId: String): List<Application> {
        val hankeIds =
            permissionService.getAllowedHankeIds(userId = userId, permission = PermissionCode.VIEW)
        return hankeRepository
            .findAllById(hankeIds)
            .flatMap { it.hakemukset }
            .map { it.toApplication() }
    }

    @Transactional(readOnly = true)
    fun getAllApplicationsCreatedByUser(userId: String): List<Application> {
        return applicationRepository.getAllByUserId(userId).map { it.toApplication() }
    }

    @Transactional(readOnly = true)
    fun getApplicationById(id: Long): Application = getById(id).toApplication()

    @Transactional
    fun create(application: Application, userId: String): Application {
        logger.info("Creating a new application for user $userId")

        validateGeometry(application.applicationData) { validationError ->
            "Invalid geometry received when creating a new application for user $userId, reason = ${validationError.reason}, location = ${validationError.location}"
        }

        val hanke =
            hankeRepository.findByHankeTunnus(application.hankeTunnus)
                ?: throw HankeNotFoundException(application.hankeTunnus)

        if (!hanke.generated) {
            application.applicationData.areas?.let { areas ->
                checkApplicationAreasInsideHankealue(hanke.id, areas) { geometry ->
                    "Application geometry doesn't match any hankealue when creating a new application for user $userId, " +
                        "hankeId = ${hanke.id}, application geometry = ${geometry.toJsonString()}"
                }
            }
        }

        val applicationEntity =
            ApplicationEntity(
                id = 0,
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
    fun updateApplicationData(
        id: Long,
        newApplicationData: ApplicationData,
        userId: String,
    ): Application {
        val application = getById(id)
        val previousApplication = application.toApplication()

        application.alluid?.let {
            throw ApplicationAlreadySentException(
                application.id,
                application.alluid,
                application.alluStatus
            )
        } ?: logger.info("Updating application id=$id")

        if (previousApplication.applicationData == newApplicationData) {
            logger.info {
                "Not updating unchanged application data. id=$id, identifier=${application.applicationIdentifier}"
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
            is ExcavationNotificationData ->
                // no support for excavation notification in old service
                throw NotImplementedError("Excavation notification not implemented")
        }

        validateGeometry(newApplicationData) { validationError ->
            "Invalid geometry received when updating application for user $userId, id=${application.id}, reason = ${validationError.reason}, location = ${validationError.location}"
        }

        val hanke = application.hanke
        if (!hanke.generated) {
            newApplicationData.areas?.let { areas ->
                checkApplicationAreasInsideHankealue(hanke.id, areas) { geometry ->
                    "Application geometry doesn't match any hankealue when updating application for user $userId, " +
                        "hankeId = ${hanke.id}, applicationId = ${application.id}, " +
                        "application geometry = ${geometry.toJsonString()}"
                }
            }
        } else {
            updateHankealueetFromApplicationData(hanke, newApplicationData)
        }

        // Don't change a draft to a non-draft or vice-versa with the update method.
        val pending = application.applicationData.pendingOnClient
        application.applicationData = newApplicationData.copy(pendingOnClient = pending)

        val saved = applicationRepository.save(application)

        return saved.toApplication().also {
            logger.info("Updated application id=${it.id}")
            applicationLoggingService.logUpdate(previousApplication, it, userId)
        }
    }

    @Transactional
    fun sendApplication(id: Long, userId: String): Application {
        val application = getById(id)

        val hanke = application.hanke
        if (!hanke.generated) {
            application.applicationData.areas?.let { areas ->
                checkApplicationAreasInsideHankealue(hanke.id, areas) { geometry ->
                    "Application geometry doesn't match any hankealue when sending application for user $userId, " +
                        "hankeId = ${hanke.id}, applicationId = ${application.id}, " +
                        "application geometry = ${geometry.toJsonString()}"
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

    /**
     * Deletes an application. Cancels the application in Allu if it's still pending. Refuses to
     * delete, if the application is in Allu, and it's beyond the pending status.
     */
    @Transactional
    fun delete(applicationId: Long, userId: String) =
        with(getById(applicationId)) { cancelAndDelete(this, userId) }

    /**
     * Deletes an application. Cancels the application in Allu if it's still pending. Refuses to
     * delete, if the application is in Allu, and it's beyond the pending status.
     *
     * Furthermore, if the owning Hanke is generated and has no more applications, also deletes the
     * Hanke.
     */
    @Transactional
    fun deleteWithOrphanGeneratedHankeRemoval(
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
    fun downloadDecision(applicationId: Long, userId: String): Pair<String, ByteArray> {
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
    fun isStillPending(alluId: Int?, alluStatus: ApplicationStatus?): Boolean =
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

    private fun cancelAndDelete(applicationEntity: ApplicationEntity, userId: String) =
        with(applicationEntity) {
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
            val application = toApplication()
            attachmentService.deleteAllAttachments(applicationEntity)
            applicationRepository.delete(this)
            applicationLoggingService.logDelete(application, userId)
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

    private fun provideAccess(
        application: ApplicationEntity,
        hanke: HankeEntity,
        currentKayttaja: HankekayttajaEntity?,
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
                hanke.nimi,
                application.applicationType,
                currentKayttaja,
                email,
            )
        }
    }

    private fun notifyOnApplication(
        hankeTunnus: String,
        hankeNimi: String,
        applicationType: ApplicationType,
        currentKayttaja: HankekayttajaEntity?,
        recipientEmail: String,
    ) {
        logger.info { "Sending Application notification." }

        if (currentKayttaja == null) {
            logger.warn { "Sending kayttaja is null, will not send application notification." }
            return
        }

        emailSenderService.sendApplicationNotificationEmail(
            ApplicationNotificationData(
                senderName = currentKayttaja.fullName(),
                senderEmail = currentKayttaja.sahkoposti,
                recipientEmail = recipientEmail,
                applicationType = applicationType,
                hankeTunnus = hankeTunnus,
                hankeNimi = hankeNimi,
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
            geometriatDao.validateGeometriat(areas.flatMap { it.geometries() })?.let {
                throw ApplicationGeometryException(customMessageOnFailure(it))
            }
        }
    }

    fun checkApplicationAreasInsideHankealue(
        hankeId: Int,
        areas: List<ApplicationArea>,
        customMessageOnFailure: (Polygon) -> String
    ) {
        areas.forEach { area ->
            when (area) {
                is CableReportApplicationArea -> {
                    if (!geometriatDao.isInsideHankeAlueet(hankeId, area.geometry))
                        throw ApplicationGeometryNotInsideHankeException(
                            customMessageOnFailure(area.geometry)
                        )
                }
                is ExcavationNotificationArea -> {
                    area.tyoalueet.forEach { tyoalue ->
                        if (!geometriatDao.isInsideHankeAlue(area.hankealueId, tyoalue.geometry))
                            throw ApplicationGeometryNotInsideHankeException(
                                customMessageOnFailure(tyoalue.geometry)
                            )
                    }
                }
            }
        }
    }

    private fun updateHankealueetFromApplicationData(
        hanke: HankeEntity,
        newApplicationData: CableReportApplicationData,
    ) {
        val hankealueet = HankealueService.createHankealueetFromCableReport(newApplicationData)
        hanke.alueet.clear()
        hanke.alueet.addAll(hankealueService.createAlueetFromCreateRequest(hankealueet, hanke))
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
                updateCableReportInAllu(entity.id, alluId, entity.hanke.hankeTunnus, data)
            is ExcavationNotificationData ->
                TODO("Sending excavation notification to Allu not implemented.")
        }

        return alluId
    }

    /** Creates new application in Allu. All attachments are sent after creation. */
    private fun createApplicationInAllu(entity: ApplicationEntity): Int {
        ensureValidForSend(entity.applicationData)
        val alluId =
            when (val data = entity.applicationData) {
                is CableReportApplicationData ->
                    createCableReportToAllu(entity.id, entity.hanke.hankeTunnus, data)
                is ExcavationNotificationData ->
                    TODO("Sending excavation notification to Allu not implemented.")
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
            withDisclosureLogging(applicationId, alluData) { cableReportService.create(alluData) }
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
            withDisclosureLogging(applicationId, alluData) {
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
        cableReportApplicationData: AlluCableReportApplicationData,
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
            // There was an exception outside login, so there was at least an attempt to send the
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
    private fun geometryMapFrom(hanke: HankeEntity): Map<Int, Geometriat?> =
        hanke.alueet
            .mapNotNull { it.geometriat }
            .associateWith { geometriatDao.retrieveGeometriat(it) }
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

class ApplicationAlreadySentException(id: Long?, alluid: Int?, status: ApplicationStatus?) :
    RuntimeException("Application is already sent to Allu, id=$id, alluId=$alluid, status=$status")

class ApplicationAlreadyProcessingException(id: Long?, alluid: Int?) :
    RuntimeException("Application is no longer pending in Allu, id=$id, alluId=$alluid")

class ApplicationGeometryException(message: String) : RuntimeException(message)

class ApplicationGeometryNotInsideHankeException(message: String) : RuntimeException(message)

class ApplicationDecisionNotFoundException(message: String) : RuntimeException(message)

class ApplicationArgumentException(message: String) : RuntimeException(message)
