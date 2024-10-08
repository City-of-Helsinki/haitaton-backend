package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeMapper
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.email.ApplicationNotificationData
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.email.JohtoselvitysCompleteEmail
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HakemusLoggingService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.paatos.PaatosService
import fi.hel.haitaton.hanke.pdf.EnrichedKaivuilmoitusalue
import fi.hel.haitaton.hanke.pdf.JohtoselvityshakemusPdfEncoder
import fi.hel.haitaton.hanke.pdf.KaivuilmoitusPdfEncoder
import fi.hel.haitaton.hanke.permissions.CurrentUserWithoutKayttajaException
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.toJsonString
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.reflect.KClass
import mu.KotlinLogging
import org.geojson.Polygon
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

const val ALLU_APPLICATION_ERROR_MSG = "Error sending application to Allu"
const val ALLU_USER_CANCELLATION_MSG = "Käyttäjä perui hakemuksen Haitattomassa."
const val ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG =
    "Haitaton ei saanut lisättyä hakemuksen liitteitä. Hakemus peruttu."

@Service
class HakemusService(
    private val hakemusRepository: HakemusRepository,
    private val hankeRepository: HankeRepository,
    private val geometriatDao: GeometriatDao,
    private val hankealueService: HankealueService,
    private val hakemusLoggingService: HakemusLoggingService,
    private val hankeLoggingService: HankeLoggingService,
    private val disclosureLogService: DisclosureLogService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val attachmentService: ApplicationAttachmentService,
    private val alluClient: AlluClient,
    private val alluStatusRepository: AlluStatusRepository,
    private val emailSenderService: EmailSenderService,
    private val paatosService: PaatosService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun getById(applicationId: Long): Hakemus = getEntityById(applicationId).toHakemus()

    @Transactional(readOnly = true)
    fun getWithPaatokset(applicationId: Long): HakemusWithPaatokset {
        val hakemus = getById(applicationId)
        val paatokset = paatosService.findByHakemusId(applicationId)
        return HakemusWithPaatokset(hakemus, paatokset)
    }

    @Transactional(readOnly = true)
    fun hakemusResponse(applicationId: Long): HakemusResponse = getById(applicationId).toResponse()

    @Transactional(readOnly = true)
    fun hankkeenHakemuksetResponse(hankeTunnus: String): HankkeenHakemuksetResponse {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)
        return HankkeenHakemuksetResponse(
            hanke.hakemukset.map { hakemus -> HankkeenHakemusResponse(hakemus) })
    }

    @Transactional
    fun create(createHakemusRequest: CreateHakemusRequest, userId: String): Hakemus {
        val hanke =
            hankeRepository.findByHankeTunnus(createHakemusRequest.hankeTunnus)
                ?: throw HankeNotFoundException(createHakemusRequest.hankeTunnus)
        val entity =
            hakemusRepository.save(
                HakemusEntity(
                    id = 0,
                    alluid = null,
                    alluStatus = null,
                    applicationIdentifier = null,
                    userId = userId,
                    applicationType = createHakemusRequest.applicationType,
                    hakemusEntityData = newApplicationData(createHakemusRequest),
                    hanke = hanke))
        val hakemus = entity.toHakemus()
        hakemusLoggingService.logCreate(hakemus, userId)
        return hakemus
    }

    /** Create a johtoselvitys from a hanke that was just created. */
    @Transactional
    fun createJohtoselvitys(hanke: HankeEntity, currentUserId: String): Hakemus {
        val data =
            JohtoselvityshakemusEntityData(
                name = hanke.nimi,
                applicationType = ApplicationType.CABLE_REPORT,
                pendingOnClient = true,
                areas = null,
                startTime = null,
                endTime = null,
                rockExcavation = null,
                workDescription = "",
            )
        val entity =
            HakemusEntity(
                id = 0,
                alluid = null,
                alluStatus = null,
                applicationIdentifier = null,
                userId = currentUserId,
                applicationType = ApplicationType.CABLE_REPORT,
                hakemusEntityData = data,
                hanke = hanke,
                yhteystiedot = mutableMapOf())

        val hakemus = hakemusRepository.save(entity).toHakemus()
        hakemusLoggingService.logCreate(hakemus, currentUserId)
        return hakemus
    }

    @Transactional
    fun updateHakemus(
        applicationId: Long,
        request: HakemusUpdateRequest,
        userId: String
    ): HakemusResponse {
        logger.info("Updating hakemus id=$applicationId")

        val applicationEntity = getEntityById(applicationId)
        val hakemus = applicationEntity.toHakemus() // the original state for audit logging

        assertNotSent(applicationEntity)
        assertCompatibility(applicationEntity, request)

        if (!request.hasChanges(applicationEntity)) {
            logger.info("Not updating unchanged hakemus data. ${applicationEntity.logString()}")
            return hakemus.toResponse()
        }

        assertGeometryValidity(request.areas) { validationError ->
            "Invalid geometry received when updating hakemus. ${applicationEntity.logString()}, reason=${validationError.reason}, location=${validationError.location}"
        }

        assertYhteystiedotValidity(applicationEntity, request)

        val hankeEntity = applicationEntity.hanke
        if (!hankeEntity.generated) {
            request.areas?.let { areas ->
                assertGeometryCompatibility(hankeEntity.id, areas) { geometry ->
                    "Hakemus geometry doesn't match any hankealue when updating hakemus. " +
                        "${applicationEntity.logString()}, ${hankeEntity.logString()}, " +
                        "hakemus geometry=${geometry.toJsonString()}"
                }
            }
        } else if (request is JohtoselvityshakemusUpdateRequest) {
            updateHankealueet(hankeEntity, request)
        }

        val updatedHakemus = saveWithUpdate(applicationEntity, request, userId).toHakemus()

        logger.info("Updated hakemus. ${updatedHakemus.logString()}")
        hakemusLoggingService.logUpdate(hakemus, updatedHakemus, userId)

        return updatedHakemus.toResponse()
    }

    @Transactional
    fun sendHakemus(id: Long, currentUserId: String): Hakemus {
        val hakemus = getEntityById(id)

        HakemusDataValidator.ensureValidForSend(hakemus.toHakemus().applicationData)

        setOrdererOnSend(hakemus, currentUserId)

        val hanke = hakemus.hanke
        if (!hanke.generated) {
            hakemus.hakemusEntityData.areas?.let { areas ->
                assertGeometryCompatibility(hanke.id, areas) { geometry ->
                    "Hakemus geometry doesn't match any hankealue when sending hakemus, " +
                        "${hanke.logString()}, ${hakemus.logString()}, " +
                        "hakemus geometry=${geometry.toJsonString()}"
                }
            }
        }

        assertNotSent(hakemus)

        // The application should no longer be a draft
        hakemus.hakemusEntityData = hakemus.hakemusEntityData.copy(pendingOnClient = false)

        // For excavation announcements, a cable report can be applied for at the same time.
        // If so, it should be sent before the excavation announcement.
        val accompanyingJohtoselvityshakemus =
            createAccompanyingJohtoselvityshakemus(hakemus, currentUserId)
        if (accompanyingJohtoselvityshakemus != null) {
            val sentJohtoselvityshakemus =
                sendHakemus(accompanyingJohtoselvityshakemus.id, currentUserId)
            // add the application identifier of the cable report to the list of cable reports in
            // the excavation announcement
            val hakemusEntityData = hakemus.hakemusEntityData as KaivuilmoitusEntityData
            val cableReports = hakemusEntityData.cableReports ?: emptyList()
            hakemus.hakemusEntityData =
                hakemusEntityData.copy(
                    cableReports =
                        cableReports.plus(sentJohtoselvityshakemus.applicationIdentifier!!))
        }

        logger.info("Sending hakemus id=$id")
        hakemus.alluid = createApplicationInAllu(hakemus.toHakemus())

        logger.info { "Hakemus sent, fetching identifier and status. ${hakemus.logString()}" }
        getApplicationInformationFromAllu(hakemus.alluid!!)?.let { response ->
            hakemus.applicationIdentifier = response.applicationId
            hakemus.alluStatus = response.status
        }

        logger.info("Sent hakemus. ${hakemus.logString()}, alluStatus = ${hakemus.alluStatus}")
        // Save only if sendApplicationToAllu didn't throw an exception
        return hakemusRepository.save(hakemus).toHakemus()
    }

    /**
     * Create a new cable report application for an excavation announcement that has a cable report
     * applied for.
     *
     * @return the new cable report application entity or null if no new application was applied for
     */
    private fun createAccompanyingJohtoselvityshakemus(
        hakemus: HakemusEntity,
        currentUserId: String,
    ): HakemusEntity? =
        when (val hakemusData = hakemus.hakemusEntityData) {
            is KaivuilmoitusEntityData ->
                if (!hakemusData.cableReportDone)
                    createAccompanyingJohtoselvityshakemus(hakemus, hakemusData, currentUserId)
                else null
            else -> null
        }

    private fun createAccompanyingJohtoselvityshakemus(
        hakemus: HakemusEntity,
        hakemusData: KaivuilmoitusEntityData,
        currentUserId: String,
    ): HakemusEntity {
        logger.info(
            "Creating a new johtoselvityshakemus for a kaivuilmoitus. ${hakemus.logString()}")
        val johtoselvityshakemusData = hakemusData.createAccompanyingJohtoselvityshakemusData()
        val johtoselvityshakemus =
            HakemusEntity(
                id = 0,
                alluid = null,
                alluStatus = null,
                applicationIdentifier = null,
                userId = currentUserId,
                applicationType = ApplicationType.CABLE_REPORT,
                hakemusEntityData = johtoselvityshakemusData,
                hanke = hakemus.hanke,
            )
        johtoselvityshakemus.yhteystiedot.putAll(
            hakemus.yhteystiedot.mapValues { it.value.copyWithHakemus(johtoselvityshakemus) })
        val savedJohtoselvityshakemus = hakemusRepository.save(johtoselvityshakemus)
        copyOtherAttachments(hakemus, savedJohtoselvityshakemus)
        logger.info(
            "Created a new johtoselvityshakemus (${savedJohtoselvityshakemus.logString()}) for a kaivuilmoitus. ${hakemus.logString()}")
        hakemusLoggingService.logCreate(savedJohtoselvityshakemus.toHakemus(), currentUserId)
        return savedJohtoselvityshakemus
    }

    /**
     * Copy other attachments from excavation announcement to cable report application. This is used
     * when a cable report is applied for at the same time as an excavation announcement is sent.
     */
    private fun copyOtherAttachments(
        kaivuilmoitus: HakemusEntity,
        johtoselvityshakemus: HakemusEntity
    ) {
        val johtoselvityshakemusMetadata = johtoselvityshakemus.toMetadata()
        attachmentService
            .getMetadataList(kaivuilmoitus.id)
            .filter { it.attachmentType == ApplicationAttachmentType.MUU }
            .forEach { metadata ->
                val content = attachmentService.getContent(metadata.id)
                attachmentService.saveAttachment(
                    johtoselvityshakemusMetadata,
                    content.bytes,
                    metadata.fileName,
                    MediaType.parseMediaType(metadata.contentType),
                    ApplicationAttachmentType.MUU)
            }
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
        hakemusId: Long,
        userId: String,
    ): HakemusDeletionResultDto {
        val application = getEntityById(hakemusId)
        val hanke = application.hanke

        cancelAndDelete(application.toHakemus(), userId)

        if (hanke.generated && hanke.hakemukset.size == 1) {
            logger.info {
                "Hakemus was the only one of a generated Hanke, removing Hanke. ${hanke.logString()}, ${application.logString()}"
            }
            hankeRepository.delete(hanke)
            val hankeDomain =
                HankeMapper.domainFrom(hanke, hankealueService.geometryMapFrom(hanke.alueet))
            hankeLoggingService.logDelete(hankeDomain, userId)

            return HakemusDeletionResultDto(hankeDeleted = true)
        }

        return HakemusDeletionResultDto(hankeDeleted = false)
    }

    @Transactional
    fun reportOperationalCondition(hakemusId: Long, date: LocalDate) {
        val hakemus = getById(hakemusId)
        val alluid = hakemus.alluid ?: throw HakemusNotYetInAlluException(hakemus)

        val hakemusData =
            when (hakemus.applicationData) {
                is JohtoselvityshakemusData ->
                    throw WrongHakemusTypeException(
                        hakemus,
                        hakemus.applicationType,
                        listOf(ApplicationType.EXCAVATION_NOTIFICATION))
                is KaivuilmoitusData -> hakemus.applicationData
            }

        if (hakemusData.startTime == null || date.isBefore(hakemusData.startTime.toLocalDate())) {
            throw OperationalConditionDateException(
                "Date is before the hakemus start date: ${hakemusData.startTime}", date, hakemus)
        }
        if (date.isAfter(LocalDate.now())) {
            throw OperationalConditionDateException("Date is in the future.", date, hakemus)
        }

        val allowedStatuses =
            listOf(
                ApplicationStatus.PENDING,
                ApplicationStatus.HANDLING,
                ApplicationStatus.INFORMATION_RECEIVED,
                ApplicationStatus.RETURNED_TO_PREPARATION,
                ApplicationStatus.DECISIONMAKING,
                ApplicationStatus.DECISION,
            )
        if (hakemus.alluStatus !in allowedStatuses) {
            throw HakemusInWrongStatusException(hakemus, hakemus.alluStatus, allowedStatuses)
        }

        logger.info {
            "Reporting operational condition for hakemus with the date $date. ${hakemus.logString()}"
        }
        alluClient.reportOperationalCondition(alluid, date)
    }

    @Transactional(readOnly = true)
    fun downloadDecision(hakemusId: Long): Pair<String, ByteArray> {
        val hakemus = getById(hakemusId)
        val alluid =
            hakemus.alluid
                ?: throw HakemusDecisionNotFoundException(
                    "Hakemus not in Allu, so it doesn't have a decision. ${hakemus.logString()}")
        val filename = hakemus.applicationIdentifier ?: "paatos"
        val pdfBytes = alluClient.getDecisionPdf(alluid)
        return Pair(filename, pdfBytes)
    }

    @Transactional
    fun handleHakemusUpdates(
        applicationHistories: List<ApplicationHistory>,
        updateTime: OffsetDateTime
    ) {
        applicationHistories.forEach { handleHakemusUpdate(it) }
        val status = alluStatusRepository.getReferenceById(1)
        status.historyLastUpdated = updateTime
        alluStatusRepository.save(status)
    }

    private fun newApplicationData(createHakemusRequest: CreateHakemusRequest): HakemusEntityData =
        when (createHakemusRequest) {
            is CreateJohtoselvityshakemusRequest ->
                createHakemusRequest.newCableReportApplicationData()
            is CreateKaivuilmoitusRequest -> createHakemusRequest.newExcavationNotificationData()
        }

    private fun CreateJohtoselvityshakemusRequest.newCableReportApplicationData() =
        JohtoselvityshakemusEntityData(
            applicationType = ApplicationType.CABLE_REPORT,
            pendingOnClient = true,
            name = name,
            postalAddress =
                PostalAddress(StreetAddress(postalAddress?.streetAddress?.streetName), "", ""),
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            propertyConnectivity = propertyConnectivity,
            emergencyWork = emergencyWork,
            rockExcavation = rockExcavation,
            workDescription = workDescription,
            startTime = null,
            endTime = null,
            areas = null,
        )

    private fun CreateKaivuilmoitusRequest.newExcavationNotificationData() =
        KaivuilmoitusEntityData(
            applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
            pendingOnClient = true,
            name = name,
            workDescription = workDescription,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            cableReportDone = cableReportDone,
            rockExcavation = rockExcavation,
            cableReports = cableReports,
            placementContracts = placementContracts,
            requiredCompetence = requiredCompetence,
            startTime = null,
            endTime = null,
            areas = null,
        )

    private fun handleHakemusUpdate(applicationHistory: ApplicationHistory) {
        val application = hakemusRepository.getOneByAlluid(applicationHistory.applicationId)
        if (application == null) {
            logger.error {
                "Allu had events for a hakemus we don't have anymore. alluId=${applicationHistory.applicationId}"
            }
            return
        }
        applicationHistory.events
            .sortedBy { it.eventTime }
            .forEach { handleApplicationEvent(application, it) }
        hakemusRepository.save(application)
    }

    private fun handleApplicationEvent(application: HakemusEntity, event: ApplicationStatusEvent) {
        fun updateStatus() {
            application.alluStatus = event.newStatus
            application.applicationIdentifier = event.applicationIdentifier
            logger.info {
                "Updating hakemus with new status, " +
                    "id=${application.id}, " +
                    "alluId=${application.alluid}, " +
                    "application identifier=${application.applicationIdentifier}, " +
                    "new status=${application.alluStatus}, " +
                    "event time=${event.eventTime}"
            }
        }

        when (event.newStatus) {
            ApplicationStatus.DECISION -> {
                updateStatus()
                when (application.applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        sendDecisionReadyEmails(application, event.applicationIdentifier)
                    ApplicationType.EXCAVATION_NOTIFICATION ->
                        paatosService.saveKaivuilmoituksenPaatos(application, event)
                }
            }
            ApplicationStatus.OPERATIONAL_CONDITION -> {
                updateStatus()
                when (application.applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        logger.error {
                            "Got ${event.newStatus} update for a cable report. ${application.logString()}"
                        }
                    ApplicationType.EXCAVATION_NOTIFICATION ->
                        paatosService.saveKaivuilmoituksenToiminnallinenKunto(application, event)
                }
            }
            ApplicationStatus.FINISHED -> {
                updateStatus()
                when (application.applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        logger.error {
                            "Got ${event.newStatus} update for a cable report. ${application.logString()}"
                        }
                    ApplicationType.EXCAVATION_NOTIFICATION ->
                        paatosService.saveKaivuilmoituksenTyoValmis(application, event)
                }
            }
            ApplicationStatus.REPLACED -> {
                logger.info {
                    "A decision has been replaced. Marking the old decisions as replaced. hakemustunnus = ${event.applicationIdentifier}, ${application.logString()}"
                }
                // Don't update application status or identifier. A new decision has been
                // made at the same time, so take the status and identifier from that update.
                paatosService.markReplaced(event.applicationIdentifier)
            }
            else -> updateStatus()
        }
    }

    private fun sendDecisionReadyEmails(application: HakemusEntity, applicationIdentifier: String) {
        val receivers = application.allContactUsers()

        if (receivers.isEmpty()) {
            logger.error {
                "No receivers found for decision ready email, not sending any. ${application.logString()}"
            }
            return
        }
        logger.info { "Sending hakemus ready emails to ${receivers.size} receivers" }

        receivers.forEach {
            applicationEventPublisher.publishEvent(
                JohtoselvitysCompleteEmail(it.sahkoposti, application.id, applicationIdentifier))
        }
    }

    /** Find the application entity or throw an exception. */
    private fun getEntityById(id: Long): HakemusEntity =
        hakemusRepository.findOneById(id) ?: throw HakemusNotFoundException(id)

    private fun setOrdererOnSend(hakemus: HakemusEntity, currentUserId: String) {
        val yhteyshenkilo: HakemusyhteyshenkiloEntity =
            listOf(
                    ApplicationContactType.HAKIJA,
                    ApplicationContactType.TYON_SUORITTAJA,
                    ApplicationContactType.RAKENNUTTAJA,
                    ApplicationContactType.ASIANHOITAJA)
                .asSequence()
                .mapNotNull { hakemus.yhteystiedot[it] }
                .flatMap { it.yhteyshenkilot }
                .find { it.hankekayttaja.permission?.userId == currentUserId }
                ?: throw UserNotInContactsException(hakemus)
        yhteyshenkilo.tilaaja = true
    }

    /** Assert that the application has not been sent to Allu. */
    private fun assertNotSent(hakemusEntity: HakemusEntity) {
        if (hakemusEntity.alluid != null) {
            throw HakemusAlreadySentException(
                hakemusEntity.id, hakemusEntity.alluid, hakemusEntity.alluStatus)
        }
    }

    private fun getApplicationInformationFromAllu(alluid: Int): AlluApplicationResponse? {
        return try {
            alluClient.getApplicationInformation(alluid)
        } catch (e: Exception) {
            logger.error(e) { "Exception while getting hakemus information." }
            null
        }
    }

    /** Creates new application in Allu. All attachments are sent after creation. */
    private fun createApplicationInAllu(hakemus: Hakemus): Int {
        val alluId =
            createApplicationToAllu(hakemus.id, hakemus.hankeTunnus, hakemus.applicationData)
        try {
            attachmentService.sendInitialAttachments(alluId, hakemus.id)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while sending the initial attachments. Canceling the hakemus. ${hakemus.logString()}"
            }
            alluClient.cancel(alluId)
            alluClient.sendSystemComment(alluId, ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG)
            throw e
        }

        return alluId
    }

    private fun createApplicationToAllu(
        applicationId: Long,
        hankeTunnus: String,
        hakemusData: HakemusData
    ): Int {
        val alluData = hakemusData.toAlluData(hankeTunnus)

        return withFormDataPdfUploading(applicationId, hankeTunnus, hakemusData) {
            withDisclosureLogging(applicationId, alluData) { alluClient.create(alluData) }
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
        applicationId: Long,
        hankeTunnus: String,
        hakemusData: HakemusData,
        alluAction: () -> Int,
    ): Int {
        val formAttachment = getApplicationDataAsPdf(applicationId, hankeTunnus, hakemusData)

        val alluId = alluAction()

        try {
            alluClient.addAttachment(alluId, formAttachment)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while uploading form data PDF attachment. Continuing anyway. alluid=$alluId"
            }
        }

        return alluId
    }

    private fun getApplicationDataAsPdf(
        applicationId: Long,
        hankeTunnus: String,
        data: HakemusData,
    ): Attachment {
        logger.info { "Creating a PDF from the hakemus data for data attachment." }
        val totalArea =
            geometriatDao.calculateCombinedArea(data.areas?.flatMap { it.geometries() } ?: listOf())

        val attachments = attachmentService.getMetadataList(applicationId)
        val pdfData =
            when (data) {
                is JohtoselvityshakemusData -> {
                    val areas =
                        data.areas?.map { geometriatDao.calculateArea(it.geometry) } ?: listOf()
                    JohtoselvityshakemusPdfEncoder.createPdf(data, totalArea, areas, attachments)
                }
                is KaivuilmoitusData -> {
                    val areas =
                        data.areas?.associate {
                            it.hankealueId to
                                geometriatDao.calculateCombinedArea(
                                    it.tyoalueet.map { alue -> alue.geometry })
                        } ?: mapOf()
                    val hankealueet = hankeRepository.findByHankeTunnus(hankeTunnus)?.alueet
                    val hankealueNames = hankealueet?.associate { it.id to it.nimi } ?: mapOf()
                    val alueet =
                        data.areas?.map {
                            EnrichedKaivuilmoitusalue(
                                areas[it.hankealueId],
                                hankealueNames[it.hankealueId] ?: "Nimetön hankealue",
                                it)
                        }
                    KaivuilmoitusPdfEncoder.createPdf(data, totalArea, attachments, alueet)
                }
            }

        val attachmentMetadata =
            AttachmentMetadata(
                id = null,
                mimeType = MediaType.APPLICATION_PDF_VALUE,
                name = "haitaton-form-data.pdf",
                description = "Original form data from Haitaton, dated ${LocalDateTime.now()}.",
            )
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
        alluApplicationData: AlluApplicationData,
        f: () -> T,
    ): T {
        try {
            val result = f()
            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId, alluApplicationData, Status.SUCCESS)
            return result
        } catch (e: AlluLoginException) {
            // Since the login failed we didn't send the application itself, so logging not needed.
            throw e
        } catch (e: Throwable) {
            // There was an exception outside login, so there was at least an attempt to send the
            // application to Allu. Allu might have read it and rejected it, so we should log this
            // as a disclosure event.
            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId, alluApplicationData, Status.FAILED, ALLU_APPLICATION_ERROR_MSG)
            throw e
        }
    }

    /** Assert that the update request is compatible with the application data. */
    private fun assertCompatibility(hakemusEntity: HakemusEntity, request: HakemusUpdateRequest) {
        val expected =
            when (hakemusEntity.hakemusEntityData) {
                is JohtoselvityshakemusEntityData -> request is JohtoselvityshakemusUpdateRequest
                is KaivuilmoitusEntityData -> request is KaivuilmoitusUpdateRequest
            }
        if (!expected) {
            throw IncompatibleHakemusUpdateRequestException(
                hakemusEntity, hakemusEntity.hakemusEntityData::class, request::class)
        }
    }

    /** Assert that the geometries are valid. */
    private fun assertGeometryValidity(
        areas: List<Hakemusalue>?,
        customMessageOnFailure: (GeometriatDao.InvalidDetail) -> String
    ) {
        if (areas != null) {
            geometriatDao.validateGeometriat(areas.flatMap { it.geometries() })?.let {
                throw HakemusGeometryException(customMessageOnFailure(it))
            }
        }
    }

    /**
     * Assert that the customers match and that the contacts in the update request are hanke users
     * of the application hanke.
     */
    private fun assertYhteystiedotValidity(
        hakemusEntity: HakemusEntity,
        updateRequest: HakemusUpdateRequest
    ) {
        val customersWithContacts = updateRequest.customersByRole()
        ApplicationContactType.entries.forEach {
            assertYhteystietoValidity(
                hakemusEntity, it, hakemusEntity.yhteystiedot[it], customersWithContacts[it])
        }

        assertYhteyshenkilotValidity(
            hakemusEntity.hanke,
            customersWithContacts.values
                .filterNotNull()
                .flatMap { it.contacts.map { contact -> contact.hankekayttajaId } }
                .toSet()) {
                "Invalid hanke user/users received when updating hakemus ${hakemusEntity.logString()}, invalidHankeKayttajaIds=$it"
            }
    }

    /**
     * If the request does not have a customer (i.e. the customer is either removed or has not
     * existed at all) or the customer is new (i.e. not persisted == not having yhteystietoId) or
     * the customer is the same as the existing one (i.e. the ids match) then all is well.
     *
     * Otherwise, the request is invalid.
     */
    private fun assertYhteystietoValidity(
        application: HakemusIdentifier,
        rooli: ApplicationContactType,
        hakemusyhteystietoEntity: HakemusyhteystietoEntity?,
        customerWithContacts: CustomerWithContactsRequest?
    ) {
        if (customerWithContacts == null ||
            customerWithContacts.customer.yhteystietoId == null ||
            customerWithContacts.customer.yhteystietoId == hakemusyhteystietoEntity?.id) {
            return
        }
        throw InvalidHakemusyhteystietoException(
            application,
            rooli,
            hakemusyhteystietoEntity?.id,
            customerWithContacts.customer.yhteystietoId)
    }

    /** Assert that the contacts are users of the hanke. */
    private fun assertYhteyshenkilotValidity(
        hanke: HankeEntity,
        newHankekayttajaIds: Set<UUID>,
        customMessageOnFailure: (Set<UUID>) -> String
    ) {
        val currentHankekayttajaIds =
            hankeKayttajaService.getKayttajatByHankeId(hanke.id).map { it.id }.toSet()
        val newInvalidHankekayttajaIds = newHankekayttajaIds.minus(currentHankekayttajaIds)
        if (newInvalidHankekayttajaIds.isNotEmpty()) {
            throw InvalidHakemusyhteyshenkiloException(
                customMessageOnFailure(newInvalidHankekayttajaIds))
        }
    }

    /** Assert that the geometries are compatible with the hanke area geometries. */
    private fun assertGeometryCompatibility(
        hankeId: Int,
        areas: List<Hakemusalue>,
        customMessageOnFailure: (Polygon) -> String
    ) {
        areas.forEach { area ->
            when (area) {
                // for cable report we check that the geometry is inside any of the hanke areas
                is JohtoselvitysHakemusalue -> {
                    if (!geometriatDao.isInsideHankeAlueet(hankeId, area.geometry))
                        throw HakemusGeometryNotInsideHankeException(
                            customMessageOnFailure(area.geometry))
                }
                // for excavation notification we check that all the tyoalue geometries are inside
                // the same hanke area
                is KaivuilmoitusAlue -> {
                    area.tyoalueet.forEach { tyoalue ->
                        if (!geometriatDao.isInsideHankeAlue(area.hankealueId, tyoalue.geometry))
                            throw HakemusGeometryNotInsideHankeException(
                                customMessageOnFailure(tyoalue.geometry))
                    }
                }
            }
        }
    }

    /** Update the hanke areas based on the update request areas. */
    private fun updateHankealueet(
        hankeEntity: HankeEntity,
        updateRequest: JohtoselvityshakemusUpdateRequest
    ) {
        val hankealueet =
            HankealueService.createHankealueetFromApplicationAreas(
                updateRequest.areas, updateRequest.startTime, updateRequest.endTime)
        hankeEntity.alueet.clear()
        hankeEntity.alueet.addAll(
            hankealueService.createAlueetFromCreateRequest(hankealueet, hankeEntity))
    }

    /** Creates a new [HakemusEntity] based on the given [request] and saves it. */
    private fun saveWithUpdate(
        hakemusEntity: HakemusEntity,
        request: HakemusUpdateRequest,
        userId: String,
    ): HakemusEntity {
        val originalContactUserIds = hakemusEntity.allContactUsers().map { it.id }.toSet()
        val updatedApplicationEntity =
            hakemusEntity.copy(
                hakemusEntityData = request.toEntityData(hakemusEntity.hakemusEntityData),
                yhteystiedot =
                    updateYhteystiedot(
                        hakemusEntity, hakemusEntity.yhteystiedot, request.customersByRole()))
        if (updatedApplicationEntity.hanke.generated) {
            updatedApplicationEntity.hanke.nimi = request.name
        }
        sendApplicationNotifications(updatedApplicationEntity, originalContactUserIds, userId)
        return hakemusRepository.save(updatedApplicationEntity)
    }

    private fun updateYhteystiedot(
        hakemusEntity: HakemusEntity,
        currentYhteystiedot: Map<ApplicationContactType, HakemusyhteystietoEntity>,
        newYhteystiedot: Map<ApplicationContactType, CustomerWithContactsRequest?>
    ): MutableMap<ApplicationContactType, HakemusyhteystietoEntity> {
        val updatedYhteystiedot = mutableMapOf<ApplicationContactType, HakemusyhteystietoEntity>()
        ApplicationContactType.entries.forEach { rooli ->
            updateYhteystieto(
                    rooli, hakemusEntity, currentYhteystiedot[rooli], newYhteystiedot[rooli])
                ?.let { updatedYhteystiedot[rooli] = it }
        }
        return updatedYhteystiedot
    }

    private fun updateYhteystieto(
        rooli: ApplicationContactType,
        hakemusEntity: HakemusEntity,
        hakemusyhteystietoEntity: HakemusyhteystietoEntity?,
        customerWithContactsRequest: CustomerWithContactsRequest?
    ): HakemusyhteystietoEntity? {
        if (customerWithContactsRequest == null) {
            // customer was deleted
            return null
        }
        if (hakemusyhteystietoEntity == null) {
            // new customer was added
            return customerWithContactsRequest.toNewHakemusyhteystietoEntity(rooli, hakemusEntity)
        }
        // update existing customer
        return customerWithContactsRequest.toExistingHakemusyhteystietoEntity(
            hakemusyhteystietoEntity)
    }

    private fun CustomerWithContactsRequest.toNewHakemusyhteystietoEntity(
        rooli: ApplicationContactType,
        hakemusEntity: HakemusEntity
    ) =
        HakemusyhteystietoEntity(
                tyyppi = customer.type,
                rooli = rooli,
                nimi = customer.name,
                sahkoposti = customer.email,
                puhelinnumero = customer.phone,
                ytunnus = customer.registryKey,
                application = hakemusEntity,
            )
            .apply {
                yhteyshenkilot.addAll(contacts.map { it.toNewHakemusyhteyshenkiloEntity(this) })
            }

    private fun ContactRequest.toNewHakemusyhteyshenkiloEntity(
        hakemusyhteystietoEntity: HakemusyhteystietoEntity
    ) =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = hakemusyhteystietoEntity,
            hankekayttaja =
                hankeKayttajaService.getKayttajaForHanke(
                    hankekayttajaId, hakemusyhteystietoEntity.application.hanke.id),
            tilaaja = false)

    private fun CustomerWithContactsRequest.toExistingHakemusyhteystietoEntity(
        hakemusyhteystietoEntity: HakemusyhteystietoEntity
    ): HakemusyhteystietoEntity {
        hakemusyhteystietoEntity.tyyppi = customer.type
        hakemusyhteystietoEntity.nimi = customer.name
        hakemusyhteystietoEntity.sahkoposti = customer.email
        hakemusyhteystietoEntity.puhelinnumero = customer.phone
        hakemusyhteystietoEntity.ytunnus = customer.registryKey
        hakemusyhteystietoEntity.yhteyshenkilot.update(hakemusyhteystietoEntity, this.contacts)
        return hakemusyhteystietoEntity
    }

    private fun MutableList<HakemusyhteyshenkiloEntity>.update(
        hakemusyhteystietoEntity: HakemusyhteystietoEntity,
        contacts: List<ContactRequest>
    ) {
        val existingIds = this.map { it.hankekayttaja.id }.toSet()
        val newIds = contacts.map { it.hankekayttajaId }.toSet()
        val toRemove = existingIds.minus(newIds)
        val toAdd = newIds.minus(existingIds)
        this.removeIf { toRemove.contains(it.hankekayttaja.id) }
        this.addAll(
            toAdd.map {
                ContactRequest(it).toNewHakemusyhteyshenkiloEntity(hakemusyhteystietoEntity)
            })
    }

    private fun sendApplicationNotifications(
        hakemusEntity: HakemusEntity,
        excludedUserIds: Set<UUID>,
        userId: String,
    ) {
        val newContacts =
            hakemusEntity.allContactUsers().filterNot { excludedUserIds.contains(it.id) }
        if (newContacts.isEmpty()) {
            return
        }

        val inviter =
            hankeKayttajaService.getKayttajaByUserId(hakemusEntity.hanke.id, userId)
                ?: throw CurrentUserWithoutKayttajaException(userId)

        for (newContact in newContacts.filter { it.sahkoposti != inviter.sahkoposti }) {
            val data =
                ApplicationNotificationData(
                    inviter.fullName(),
                    inviter.sahkoposti,
                    newContact.sahkoposti,
                    hakemusEntity.applicationType,
                    hakemusEntity.hanke.hankeTunnus,
                    hakemusEntity.hanke.nimi,
                )
            emailSenderService.sendApplicationNotificationEmail(data)
        }
    }

    @Transactional
    fun cancelAndDelete(hakemus: Hakemus, userId: String) {
        if (hakemus.alluid == null) {
            logger.info {
                "Hakemus not sent to Allu yet, simply deleting it. ${hakemus.logString()}"
            }
        } else {
            logger.info {
                "Hakemus is sent to Allu, canceling it before deleting. ${hakemus.logString()}"
            }
            cancelHakemus(hakemus.id, hakemus.alluid, hakemus.alluStatus)
        }

        logger.info { "Deleting hakemus, ${hakemus.logString()}" }
        attachmentService.deleteAllAttachments(hakemus)
        hakemusRepository.deleteById(hakemus.id)
        hakemusLoggingService.logDelete(hakemus, userId)
        logger.info { "Hakemus deleted, ${hakemus.logString()}" }
    }

    /** Cancel a hakemus that's been sent to Allu. */
    private fun cancelHakemus(id: Long?, alluId: Int, alluStatus: ApplicationStatus?) {
        if (isStillPending(alluId, alluStatus)) {
            logger.info { "Hakemus is still pending, trying to cancel it. id=$id alluid=${alluId}" }
            alluClient.cancel(alluId)
            alluClient.sendSystemComment(alluId, ALLU_USER_CANCELLATION_MSG)
            logger.info { "Hakemus canceled, proceeding to delete it. id=$id alluid=${alluId}" }
        } else if (isCancelled(alluStatus)) {
            logger.info {
                "Hakemus is already cancelled, proceeding to delete it. id=$id alluid=${alluId}"
            }
        } else {
            throw HakemusAlreadyProcessingException(id, alluId)
        }
    }

    /**
     * An application is being processed in Allu if status it is NOT pending anymore. Pending status
     * needs verification from Allu. A post-pending status can never go back to pending.
     */
    fun isStillPending(alluId: Int?, alluStatus: ApplicationStatus?): Boolean {
        // If there's no alluid then we haven't successfully sent this to ALLU yet (at all)
        alluId ?: return true

        return when (alluStatus) {
            null,
            ApplicationStatus.PENDING,
            ApplicationStatus.PENDING_CLIENT -> isStillPendingInAllu(alluId)
            else -> false
        }
    }

    private fun isStillPendingInAllu(alluId: Int): Boolean {
        val currentStatus = alluClient.getApplicationInformation(alluId).status

        return when (currentStatus) {
            ApplicationStatus.PENDING,
            ApplicationStatus.PENDING_CLIENT -> true
            else -> false
        }
    }

    fun isCancelled(alluStatus: ApplicationStatus?) = alluStatus == ApplicationStatus.CANCELLED
}

class IncompatibleHakemusUpdateRequestException(
    application: HakemusIdentifier,
    oldApplicationClass: KClass<out HakemusEntityData>,
    requestClass: KClass<out HakemusUpdateRequest>,
) :
    RuntimeException(
        "Invalid update request for hakemus. ${application.logString()}, type=$oldApplicationClass, requestType=$requestClass")

class InvalidHakemusyhteystietoException(
    application: HakemusIdentifier,
    rooli: ApplicationContactType,
    yhteystietoId: UUID?,
    newId: UUID?,
) :
    RuntimeException(
        "Invalid hakemusyhteystieto received when updating hakemus. ${application.logString()}, role=$rooli, yhteystietoId=$yhteystietoId, newId=$newId")

class InvalidHakemusyhteyshenkiloException(message: String) : RuntimeException(message)

class UserNotInContactsException(application: HakemusIdentifier) :
    RuntimeException("Sending user is not a contact on the hakemus. ${application.logString()}")

class HakemusNotFoundException(id: Long) : RuntimeException("Hakemus not found with id $id")

class HakemusAlreadySentException(id: Long?, alluid: Int?, status: ApplicationStatus?) :
    RuntimeException("Hakemus is already sent to Allu, id=$id, alluId=$alluid, status=$status")

class HakemusAlreadyProcessingException(id: Long?, alluid: Int?) :
    RuntimeException("Hakemus is no longer pending in Allu, id=$id, alluId=$alluid")

class HakemusGeometryException(message: String) : RuntimeException(message)

class HakemusGeometryNotInsideHankeException(message: String) : RuntimeException(message)

class HakemusDecisionNotFoundException(message: String) : RuntimeException(message)

class HakemusNotYetInAlluException(hakemus: HakemusIdentifier) :
    RuntimeException("Hakemus is not yet in Allu. ${hakemus.logString()}")

class WrongHakemusTypeException(
    hakemus: HakemusIdentifier,
    type: ApplicationType,
    allowed: List<ApplicationType>
) :
    RuntimeException(
        "Wrong application type for this action. type=$type, " +
            "allowed types=${allowed.joinToString(", ")}, ${hakemus.logString()}")

class HakemusInWrongStatusException(
    hakemus: HakemusIdentifier,
    status: ApplicationStatus?,
    allowed: List<ApplicationStatus>
) :
    RuntimeException(
        "Hakemus is in the wrong status for this operation. status=$status, " +
            "allowed statuses=${allowed.joinToString(", ")}, ${hakemus.logString()}, ")

class OperationalConditionDateException(
    error: String,
    date: LocalDate,
    hakemus: HakemusIdentifier,
) :
    RuntimeException(
        "Invalid date in operational condition report. $error date=$date, ${hakemus.logString()}")
