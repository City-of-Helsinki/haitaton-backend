package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeMapper
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.taydennys.TaydennysAttachmentMetadataService
import fi.hel.haitaton.hanke.daysBetween
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.email.ApplicationNotificationEmail
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getCurrentTimeUTCAsLocalTime
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HakemusLoggingService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.paatos.PaatosService
import fi.hel.haitaton.hanke.pdf.EnrichedKaivuilmoitusalue
import fi.hel.haitaton.hanke.pdf.HaittojenhallintasuunnitelmaPdfEncoder
import fi.hel.haitaton.hanke.pdf.JohtoselvityshakemusPdfEncoder
import fi.hel.haitaton.hanke.pdf.KaivuilmoitusPdfEncoder
import fi.hel.haitaton.hanke.permissions.CurrentUserWithoutKayttajaException
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusEntity
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import java.time.LocalDate
import java.time.LocalDateTime
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

const val FORM_DATA_PDF_FILENAME = "haitaton-form-data.pdf"
const val HHS_PDF_FILENAME = "haitaton-haittojenhallintasuunnitelma.pdf"
private const val PAPER_DECISION_MSG =
    "Asiakas haluaa päätöksen myös paperisena. Liitteessä $FORM_DATA_PDF_FILENAME on päätöksen toimitukseen liittyvät osoitetiedot."

@Service
class HakemusService(
    private val hakemusRepository: HakemusRepository,
    private val hankeRepository: HankeRepository,
    private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    private val taydennysRepository: TaydennysRepository,
    private val geometriatDao: GeometriatDao,
    private val hankealueService: HankealueService,
    private val hakemusLoggingService: HakemusLoggingService,
    private val hankeLoggingService: HankeLoggingService,
    private val disclosureLogService: DisclosureLogService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val attachmentService: ApplicationAttachmentService,
    private val taydennysAttachmentService: TaydennysAttachmentMetadataService,
    private val alluClient: AlluClient,
    private val paatosService: PaatosService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService,
) {

    @Transactional(readOnly = true)
    fun getById(applicationId: Long): Hakemus = getEntityById(applicationId).toHakemus()

    @Transactional(readOnly = true)
    fun getWithExtras(hakemusId: Long): HakemusWithExtras {
        val hakemus = getById(hakemusId)
        val paatokset = paatosService.findByHakemusId(hakemusId)
        val taydennyspyynto = taydennyspyyntoRepository.findByApplicationId(hakemusId)?.toDomain()
        val taydennys =
            taydennysRepository.findByApplicationId(hakemusId)?.let {
                val liitteet = taydennysAttachmentService.getMetadataList(it.id)
                it.toDomain().withExtras(hakemus.applicationData, liitteet)
            }

        return HakemusWithExtras(hakemus, paatokset, taydennyspyynto, taydennys)
    }

    @Transactional(readOnly = true)
    fun hankkeenHakemukset(hankeTunnus: String): List<Hakemus> {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)
        return hanke.hakemukset.map { it.toHakemus() }
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
                    hanke = hanke,
                )
            )
        val hakemus = entity.toHakemus()
        hakemusLoggingService.logCreate(hakemus, userId)
        // A hanke with a hakemus should be in RAKENTAMINEN phase
        updateHankevaiheToRakentaminen(hanke, userId)
        return hakemus
    }

    /** Update the hankevaihe of a Hanke to RAKENTAMINEN if it's not already there. */
    private fun updateHankevaiheToRakentaminen(hanke: HankeEntity, userId: String) {
        if (hanke.vaihe != Hankevaihe.RAKENTAMINEN) {
            val geometriatMap = hankealueService.geometryMapFrom(hanke.alueet)
            val hankeBeforeUpdate = HankeMapper.domainFrom(hanke, geometriatMap)
            hanke.vaihe = Hankevaihe.RAKENTAMINEN
            hanke.version = hanke.version?.inc() ?: 1
            hanke.modifiedByUserId = userId
            hanke.modifiedAt = getCurrentTimeUTCAsLocalTime()

            val savedHanke = hankeRepository.save(hanke)
            val hankeAfterUpdate = HankeMapper.domainFrom(savedHanke, geometriatMap)
            hankeLoggingService.logUpdate(hankeBeforeUpdate, hankeAfterUpdate, userId)
        }
    }

    /** Create a johtoselvitys from a hanke that was just created. */
    @Transactional
    fun createJohtoselvitys(hanke: HankeEntity, currentUserId: String): Hakemus {
        val data =
            JohtoselvityshakemusEntityData(
                name = hanke.nimi,
                applicationType = ApplicationType.CABLE_REPORT,
                areas = null,
                startTime = null,
                endTime = null,
                rockExcavation = null,
                workDescription = "",
                paperDecisionReceiver = null,
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
                yhteystiedot = mutableMapOf(),
            )

        val hakemus = hakemusRepository.save(entity).toHakemus()
        hakemusLoggingService.logCreate(hakemus, currentUserId)
        return hakemus
    }

    @Transactional
    fun updateHakemus(applicationId: Long, request: HakemusUpdateRequest, userId: String): Hakemus {
        val applicationEntity = getEntityById(applicationId)
        val hakemus = applicationEntity.toHakemus() // the original state for audit logging
        logger.info("Updating hakemus ${hakemus.logString()}")

        assertNotSent(applicationEntity)
        assertCompatibility(applicationEntity, request)

        if (!request.hasChanges(hakemus.applicationData)) {
            logger.info("Not updating unchanged hakemus data. ${applicationEntity.logString()}")
            return hakemus
        }

        assertGeometryValidity(request.areas)
        assertYhteystiedotValidity(applicationEntity.hanke, applicationEntity.yhteystiedot, request)
        assertOrUpdateHankealueet(applicationEntity.hanke, request)

        val originalContactUserIds = applicationEntity.allContactUsers().map { it.id }.toSet()
        val updatedHakemusEntity = saveWithUpdate(applicationEntity, request)
        sendHakemusNotifications(updatedHakemusEntity, originalContactUserIds, userId)

        val updatedHakemus = updatedHakemusEntity.toHakemus()
        logger.info("Updated hakemus. ${updatedHakemus.logString()}")
        hakemusLoggingService.logUpdate(hakemus, updatedHakemus, userId)

        return updatedHakemus
    }

    @Transactional
    fun sendHakemus(
        id: Long,
        paperDecisionReceiver: PaperDecisionReceiver?,
        currentUserId: String,
    ): Hakemus {
        val hakemus = getEntityById(id)
        val hanke = hakemus.hanke
        logger.info { "Sending hakemus to Allu. ${hakemus.logString()} ${hanke.logString()}" }

        if (paperDecisionReceiver != null) {
            val hakemusBefore = hakemus.toHakemus()
            hakemus.hakemusEntityData =
                hakemus.hakemusEntityData.copy(paperDecisionReceiver = paperDecisionReceiver)
            hakemusLoggingService.logUpdate(hakemusBefore, hakemus.toHakemus(), currentUserId)
        }

        HakemusDataValidator.ensureValidForSend(hakemus.toHakemus().applicationData)

        setOrdererOnSend(hakemus, currentUserId)

        if (!hanke.generated) {
            hakemus.hakemusEntityData.areas?.let { areas ->
                assertGeometryCompatibility(hanke.id, areas)
            }
        }

        assertNotSent(hakemus)

        // For excavation announcements, a cable report can be applied for at the same time.
        // If so, it should be sent before the excavation announcement.
        createAccompanyingJohtoselvityshakemus(hakemus, currentUserId)?.let {
            val sentJohtoselvityshakemus = sendHakemus(it.id, paperDecisionReceiver, currentUserId)
            // Add the application identifier of the cable report to the list of cable reports
            // in the excavation announcement
            val hakemusEntityData = hakemus.hakemusEntityData as KaivuilmoitusEntityData
            val cableReports = hakemusEntityData.cableReports ?: emptyList()
            hakemus.hakemusEntityData =
                hakemusEntityData.copy(
                    cableReports =
                        cableReports.plus(sentJohtoselvityshakemus.applicationIdentifier!!)
                )
        }

        logger.info("Sending hakemus id=$id")
        hakemus.alluid = createApplicationInAllu(hakemus.toHakemus(), hanke)

        logger.info { "Hakemus sent, fetching identifier and status. ${hakemus.logString()}" }
        updateStatusFromAllu(hakemus)

        updateCableReportDoneFlag(hakemus)

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
            "Creating a new johtoselvityshakemus for a kaivuilmoitus. ${hakemus.logString()}"
        )
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
        val yhteystiedot =
            hakemus.yhteystiedot.mapValues { it.value.copyWithHakemus(johtoselvityshakemus) }
        // In johtoselvityshakemus, person and other type customers don't need to have registry
        // keys, so they should be removed.
        yhteystiedot.values
            .filter { it.tyyppi in listOf(CustomerType.PERSON, CustomerType.OTHER) }
            .forEach { it.registryKey = null }
        johtoselvityshakemus.yhteystiedot.putAll(yhteystiedot)

        val savedJohtoselvityshakemus = hakemusRepository.save(johtoselvityshakemus)
        copyOtherAttachments(hakemus, savedJohtoselvityshakemus)
        logger.info(
            "Created a new johtoselvityshakemus (${savedJohtoselvityshakemus.logString()}) for a kaivuilmoitus. ${hakemus.logString()}"
        )
        hakemusLoggingService.logCreate(savedJohtoselvityshakemus.toHakemus(), currentUserId)
        return savedJohtoselvityshakemus
    }

    private fun updateCableReportDoneFlag(hakemus: HakemusEntity) {
        val hakemusData = hakemus.hakemusEntityData
        if (hakemusData is KaivuilmoitusEntityData && !hakemusData.cableReportDone) {
            hakemus.hakemusEntityData = hakemusData.copy(cableReportDone = true)
            logger.info(
                "Set cablereportDone as 'true' after send for accompanying johtoselvityshakemus in kaivuilmoitus. ${hakemus.logString()}"
            )
        }
    }

    /**
     * Copy other attachments from excavation announcement to cable report application. This is used
     * when a cable report is applied for at the same time as an excavation announcement is sent.
     */
    private fun copyOtherAttachments(
        kaivuilmoitus: HakemusEntity,
        johtoselvityshakemus: HakemusEntity,
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
                    ApplicationAttachmentType.MUU,
                )
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
    fun reportCompletionDate(
        ilmoitusType: ValmistumisilmoitusType,
        hakemusId: Long,
        date: LocalDate,
    ) {
        val entity = getEntityById(hakemusId)
        val hakemus = entity.toHakemus()
        val alluid = hakemus.alluid ?: throw HakemusNotYetInAlluException(hakemus)

        val hakemusData =
            when (hakemus.applicationData) {
                is JohtoselvityshakemusData ->
                    throw WrongHakemusTypeException(
                        hakemus,
                        hakemus.applicationType,
                        listOf(ApplicationType.EXCAVATION_NOTIFICATION),
                    )
                is KaivuilmoitusData -> hakemus.applicationData
            }

        if (hakemusData.startTime == null || date.isBefore(hakemusData.startTime.toLocalDate())) {
            throw CompletionDateException(
                ilmoitusType,
                "Date is before the hakemus start date: ${hakemusData.startTime}",
                date,
                hakemus,
            )
        }
        if (date.isAfter(LocalDate.now())) {
            throw CompletionDateException(ilmoitusType, "Date is in the future.", date, hakemus)
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
                .let {
                    when (ilmoitusType) {
                        ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO -> it
                        ValmistumisilmoitusType.TYO_VALMIS ->
                            it + ApplicationStatus.OPERATIONAL_CONDITION
                    }
                }
        if (hakemus.alluStatus !in allowedStatuses) {
            throw HakemusInWrongStatusException(hakemus, hakemus.alluStatus, allowedStatuses)
        }

        logger.info {
            "Reporting ${ilmoitusType.logName} for hakemus with the date $date. ${hakemus.logString()}"
        }
        alluClient.reportCompletionDate(ilmoitusType, alluid, date)
        entity.valmistumisilmoitukset.add(
            ValmistumisilmoitusEntity(
                type = ilmoitusType,
                hakemustunnus = entity.applicationIdentifier!!,
                dateReported = date,
                hakemus = entity,
            )
        )
    }

    @Transactional(readOnly = true)
    fun downloadDecision(hakemusId: Long): Pair<String, ByteArray> {
        val hakemus = getById(hakemusId)
        val alluid =
            hakemus.alluid
                ?: throw HakemusDecisionNotFoundException(
                    "Hakemus not in Allu, so it doesn't have a decision. ${hakemus.logString()}"
                )
        val filename = hakemus.applicationIdentifier ?: "paatos"
        val pdfBytes = alluClient.getDecisionPdf(alluid)
        return Pair(filename, pdfBytes)
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
            paperDecisionReceiver = null,
        )

    private fun CreateKaivuilmoitusRequest.newExcavationNotificationData() =
        KaivuilmoitusEntityData(
            applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
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
            paperDecisionReceiver = null,
        )

    /** Find the application entity or throw an exception. */
    private fun getEntityById(id: Long): HakemusEntity =
        hakemusRepository.findOneById(id) ?: throw HakemusNotFoundException(id)

    private fun setOrdererOnSend(hakemus: HakemusEntity, currentUserId: String) {
        val yhteyshenkilo: HakemusyhteyshenkiloEntity =
            listOf(
                    ApplicationContactType.HAKIJA,
                    ApplicationContactType.TYON_SUORITTAJA,
                    ApplicationContactType.RAKENNUTTAJA,
                    ApplicationContactType.ASIANHOITAJA,
                )
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
                hakemusEntity.id,
                hakemusEntity.alluid,
                hakemusEntity.alluStatus,
            )
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

    @Transactional
    fun updateStatusFromAllu(hakemus: HakemusEntity) =
        getApplicationInformationFromAllu(hakemus.alluid!!)?.let { response ->
            hakemus.applicationIdentifier = response.applicationId
            hakemus.alluStatus = response.status
        }

    /** Creates new application in Allu. All attachments are sent after creation. */
    private fun createApplicationInAllu(hakemus: Hakemus, hankeEntity: HankeEntity): Int {
        val alluId = createApplicationToAllu(hakemus.id, hankeEntity, hakemus.applicationData)
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
        hankeEntity: HankeEntity,
        hakemusData: HakemusData,
    ): Int {
        val alluData = hakemusData.toAlluData(hankeEntity.hankeTunnus)

        val alluId =
            withFormDataPdfUploading(applicationId, hankeEntity, hakemusData) {
                disclosureLogService.withDisclosureLogging(applicationId, alluData) {
                    alluClient.create(alluData)
                }
            }
        if (hakemusData.paperDecisionReceiver != null) {
            alluClient.sendSystemComment(alluId, PAPER_DECISION_MSG)
        }
        return alluId
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
        hankeEntity: HankeEntity,
        hakemusData: HakemusData,
        alluAction: () -> Int,
    ): Int {
        val formAttachment =
            getApplicationDataAsPdf(applicationId, hankeEntity.hankeTunnus, hakemusData)
        val hhsAttachment = getHaittojenhallintasuunnitelmaPdf(hankeEntity, hakemusData)

        val alluId = alluAction()

        try {
            alluClient.addAttachment(alluId, formAttachment)
            hhsAttachment?.let { alluClient.addAttachment(alluId, it) }
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
        val attachments = attachmentService.getMetadataList(applicationId)
        return getApplicationDataAsPdf(hankeTunnus, attachments, data)
    }

    fun getApplicationDataAsPdf(
        hankeTunnus: String,
        attachments: List<ApplicationAttachmentMetadata>,
        data: HakemusData,
    ): Attachment {
        val totalArea =
            geometriatDao.calculateCombinedArea(data.areas?.flatMap { it.geometries() } ?: listOf())

        val pdfData =
            when (data) {
                is JohtoselvityshakemusData -> {
                    val areas =
                        data.areas?.map { geometriatDao.calculateArea(it.geometry) } ?: listOf()
                    JohtoselvityshakemusPdfEncoder.createPdf(data, totalArea, areas, attachments)
                }
                is KaivuilmoitusData -> {
                    val alueet = data.areas?.let { enrichKaivuilmoitusalueet(it, hankeTunnus) }
                    KaivuilmoitusPdfEncoder.createPdf(data, totalArea, attachments, alueet)
                }
            }

        val attachmentMetadata =
            AttachmentMetadata(
                id = null,
                mimeType = MediaType.APPLICATION_PDF_VALUE,
                name = FORM_DATA_PDF_FILENAME,
                description = "Original form data from Haitaton, dated ${LocalDateTime.now()}.",
            )
        logger.info { "Created the PDF for data attachment." }
        return Attachment(attachmentMetadata, pdfData)
    }

    private fun enrichKaivuilmoitusalueet(
        alueet: List<KaivuilmoitusAlue>,
        hankeTunnus: String,
    ): List<EnrichedKaivuilmoitusalue> {
        val surfaceAreas =
            alueet.associate {
                it.hankealueId to
                    geometriatDao.calculateCombinedArea(it.tyoalueet.map { alue -> alue.geometry })
            }
        val hankealueet = hankeRepository.findByHankeTunnus(hankeTunnus)?.alueet
        val hankealueNames = hankealueet?.associate { it.id to it.nimi } ?: mapOf()
        return alueet.map {
            EnrichedKaivuilmoitusalue(
                surfaceAreas[it.hankealueId],
                hankealueNames[it.hankealueId] ?: "Nimetön hankealue",
                it,
            )
        }
    }

    private fun getHaittojenhallintasuunnitelmaPdf(
        hankeEntity: HankeEntity,
        data: HakemusData,
    ): Attachment? {
        if (data !is KaivuilmoitusData) return null
        logger.info { "Creating a PDF from haittojenhallintasuunnitelma." }

        // We can't use HankeService to load the hanke, because HankeService depends on this class.
        val geometriatMap = hankealueService.geometryMapFrom(hankeEntity.alueet)
        val hanke = HankeMapper.domainFrom(hankeEntity, geometriatMap)

        val totalArea =
            geometriatDao.calculateCombinedArea(data.areas?.flatMap { it.geometries() } ?: listOf())
        val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, data, totalArea)
        val attachmentMetadata =
            AttachmentMetadata(
                id = null,
                mimeType = MediaType.APPLICATION_PDF_VALUE,
                name = HHS_PDF_FILENAME,
                description =
                    "Haittojenhallintasuunnitelma from Haitaton, dated ${LocalDateTime.now()}.",
            )
        logger.info { "Created the PDF from haittojenhallintasuunnitelma." }
        return Attachment(attachmentMetadata, pdfData)
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
                hakemusEntity,
                hakemusEntity.hakemusEntityData::class,
                request::class,
            )
        }
    }

    /** Assert that the geometries are valid. */
    fun assertGeometryValidity(areas: List<Hakemusalue>?) {
        if (areas != null) {
            geometriatDao.validateGeometriat(areas.flatMap { it.geometries() })?.let {
                throw HakemusGeometryException(it)
            }
        }
    }

    /**
     * Assert that the customers match and that the contacts in the update request are hanke users
     * of the application hanke.
     */
    fun <H : YhteyshenkiloEntity> assertYhteystiedotValidity(
        hanke: HankeEntity,
        yhteystiedot: Map<ApplicationContactType, YhteystietoEntity<H>>,
        updateRequest: HakemusUpdateRequest,
    ) {
        val customersWithContacts = updateRequest.customersByRole()
        ApplicationContactType.entries.forEach { rooli ->
            val yhteystietoId = yhteystiedot[rooli]?.id
            assertYhteystietoValidity(yhteystietoId, customersWithContacts[rooli])
        }

        assertYhteyshenkilotValidity(hanke, customersWithContacts)
    }

    /**
     * If the request does not have a customer (i.e. the customer is either removed or has not
     * existed at all) or the customer is new (i.e. not persisted == not having yhteystietoId) or
     * the customer is the same as the existing one (i.e. the ids match) then all is well.
     *
     * Otherwise, the request is invalid.
     */
    private fun assertYhteystietoValidity(
        yhteystietoEntityId: UUID?,
        customerWithContacts: CustomerWithContactsRequest?,
    ) {
        val newId = customerWithContacts?.customer?.yhteystietoId
        if (newId == null || customerWithContacts.customer.yhteystietoId == yhteystietoEntityId) {
            return
        }
        throw InvalidHakemusyhteystietoException(yhteystietoEntityId, newId)
    }

    /** Assert that the contacts are users of the hanke. */
    private fun assertYhteyshenkilotValidity(
        hanke: HankeEntity,
        customersWithContacts: Map<ApplicationContactType, CustomerWithContactsRequest?>,
    ) {
        val newHankekayttajaIds =
            customersWithContacts.values
                .filterNotNull()
                .flatMap { it.contacts.map { contact -> contact.hankekayttajaId } }
                .toSet()
        val currentHankekayttajaIds =
            hankeKayttajaService.getKayttajatByHankeId(hanke.id).map { it.id }.toSet()
        val newInvalidHankekayttajaIds = newHankekayttajaIds.minus(currentHankekayttajaIds)
        if (newInvalidHankekayttajaIds.isNotEmpty()) {
            throw InvalidHakemusyhteyshenkiloException(newInvalidHankekayttajaIds)
        }
    }

    /**
     * Assert that the geometries are compatible with the hanke area geometries or update the hanke
     * geometries if this is in a generated hanke.
     */
    fun assertOrUpdateHankealueet(hankeEntity: HankeEntity, request: HakemusUpdateRequest) {
        if (!hankeEntity.generated) {
            request.areas?.let { areas -> assertGeometryCompatibility(hankeEntity.id, areas) }
        } else if (request is JohtoselvityshakemusUpdateRequest) {
            updateHankealueet(hankeEntity, request)
        }
    }

    /** Assert that the geometries are compatible with the hanke area geometries. */
    fun assertGeometryCompatibility(hankeId: Int, areas: List<Hakemusalue>) {
        areas.forEach { area ->
            when (area) {
                is JohtoselvitysHakemusalue -> assertGeometryCompatibility(hankeId, area)
                is KaivuilmoitusAlue -> assertGeometryCompatibility(area)
            }
        }
    }

    /** For cable report we check that the geometry is inside any of the hanke areas. */
    private fun assertGeometryCompatibility(hankeId: Int, area: JohtoselvitysHakemusalue) {
        if (!geometriatDao.isInsideHankeAlueet(hankeId, area.geometry))
            throw HakemusGeometryNotInsideHankeException(area.geometry)
    }

    /**
     * For excavation notification we check that all the tyoalue geometries are inside the same
     * hanke area.
     */
    private fun assertGeometryCompatibility(area: KaivuilmoitusAlue) {
        area.tyoalueet.forEach { tyoalue ->
            if (!geometriatDao.isInsideHankeAlue(area.hankealueId, tyoalue.geometry))
                throw HakemusGeometryNotInsideHankeException(area.hankealueId, tyoalue.geometry)
        }
    }

    /** Update the hanke areas based on the update request areas. */
    private fun updateHankealueet(
        hankeEntity: HankeEntity,
        updateRequest: JohtoselvityshakemusUpdateRequest,
    ) {
        val hankealueet =
            HankealueService.createHankealueetFromApplicationAreas(
                updateRequest.areas,
                updateRequest.startTime,
                updateRequest.endTime,
            )
        hankeEntity.alueet.clear()
        hankeEntity.alueet.addAll(
            hankealueService.createAlueetFromCreateRequest(hankealueet, hankeEntity)
        )
    }

    /** Creates a new [HakemusEntity] based on the given [request] and saves it. */
    private fun saveWithUpdate(
        hakemusEntity: HakemusEntity,
        request: HakemusUpdateRequest,
    ): HakemusEntity {
        logger.info { "Creating and saving new hakemus data. ${hakemusEntity.logString()}" }
        val updatedApplicationEntity =
            hakemusEntity.copy(
                hakemusEntityData = request.toEntityData(hakemusEntity.hakemusEntityData),
                yhteystiedot = updateYhteystiedot(hakemusEntity, request.customersByRole()),
            )
        if (updatedApplicationEntity.hanke.generated) {
            updatedApplicationEntity.hanke.nimi = request.name
        }
        updateTormaystarkastelut(updatedApplicationEntity.hakemusEntityData)?.let {
            updatedApplicationEntity.hakemusEntityData = it
        }
        return hakemusRepository.save(updatedApplicationEntity)
    }

    /** Calculate the traffic nuisance indexes for each work area of an application. */
    fun updateTormaystarkastelut(hakemusData: HakemusEntityData): KaivuilmoitusEntityData? {
        val kaivuilmoitusData =
            when (hakemusData) {
                is JohtoselvityshakemusEntityData -> return null
                is KaivuilmoitusEntityData -> hakemusData
            }
        if (kaivuilmoitusData.startTime == null || kaivuilmoitusData.endTime == null) {
            return null
        }
        val areas =
            kaivuilmoitusData.areas?.map { area ->
                updateTormaystarkastelutForArea(
                    area,
                    kaivuilmoitusData.startTime.toLocalDate(),
                    kaivuilmoitusData.endTime.toLocalDate(),
                )
            }
        return kaivuilmoitusData.copy(areas = areas)
    }

    private fun updateTormaystarkastelutForArea(
        area: KaivuilmoitusAlue,
        startDate: LocalDate,
        endDate: LocalDate,
    ): KaivuilmoitusAlue {
        val tyoalueet =
            area.tyoalueet.map { tyoalue ->
                val tormaystarkasteluTulos =
                    tormaystarkasteluLaskentaService.calculateTormaystarkastelu(
                        tyoalue.geometry,
                        daysBetween(startDate, endDate),
                        area.kaistahaitta,
                        area.kaistahaittojenPituus,
                    )
                tyoalue.copy(tormaystarkasteluTulos = tormaystarkasteluTulos)
            }
        return area.copy(tyoalueet = tyoalueet)
    }

    private fun updateYhteystiedot(
        hakemusEntity: HakemusEntity,
        newYhteystiedot: Map<ApplicationContactType, CustomerWithContactsRequest?>,
    ): MutableMap<ApplicationContactType, HakemusyhteystietoEntity> {
        val updatedYhteystiedot = mutableMapOf<ApplicationContactType, HakemusyhteystietoEntity>()
        ApplicationContactType.entries.forEach { rooli ->
            updateYhteystieto(rooli, hakemusEntity, newYhteystiedot[rooli])?.let {
                updatedYhteystiedot[rooli] = it
            }
        }
        return updatedYhteystiedot
    }

    private fun updateYhteystieto(
        rooli: ApplicationContactType,
        hakemusEntity: HakemusEntity,
        customerWithContactsRequest: CustomerWithContactsRequest?,
    ): HakemusyhteystietoEntity? {
        if (customerWithContactsRequest == null) {
            // customer was deleted
            return null
        }
        return hakemusEntity.yhteystiedot[rooli]?.let {
            val newHenkilot = customerWithContactsRequest.toExistingYhteystietoEntity(it)
            newHenkilot.map { hankekayttajaId ->
                it.yhteyshenkilot.add(newHakemusyhteyshenkiloEntity(hankekayttajaId, it))
            }
            it
        } ?: customerWithContactsRequest.toNewHakemusyhteystietoEntity(rooli, hakemusEntity)
    }

    private fun CustomerWithContactsRequest.toNewHakemusyhteystietoEntity(
        rooli: ApplicationContactType,
        hakemusEntity: HakemusEntity,
    ) =
        HakemusyhteystietoEntity(
                tyyppi = customer.type,
                rooli = rooli,
                nimi = customer.name,
                sahkoposti = customer.email,
                puhelinnumero = customer.phone,
                registryKey = customer.registryKey,
                application = hakemusEntity,
            )
            .apply {
                yhteyshenkilot.addAll(
                    contacts.map { newHakemusyhteyshenkiloEntity(it.hankekayttajaId, this) }
                )
            }

    private fun newHakemusyhteyshenkiloEntity(
        hankekayttajaId: UUID,
        hakemusyhteystietoEntity: HakemusyhteystietoEntity,
    ) =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = hakemusyhteystietoEntity,
            hankekayttaja =
                hankeKayttajaService.getKayttajaForHanke(
                    hankekayttajaId,
                    hakemusyhteystietoEntity.application.hanke.id,
                ),
            tilaaja = false,
        )

    private fun sendHakemusNotifications(
        hakemusEntity: HakemusEntity,
        excludedUserIds: Set<UUID>,
        userId: String,
    ) {
        val newContacts =
            hakemusEntity.allContactUsers().filterNot { excludedUserIds.contains(it.id) }
        if (newContacts.isNotEmpty()) {
            sendHakemusNotifications(newContacts, hakemusEntity, userId)
        }
    }

    fun sendHakemusNotifications(
        newContacts: List<HankekayttajaEntity>,
        hakemusEntity: HakemusEntity,
        userId: String,
    ) {
        val inviter =
            hankeKayttajaService.getKayttajaByUserId(hakemusEntity.hanke.id, userId)
                ?: throw CurrentUserWithoutKayttajaException(userId)

        for (newContact in newContacts.filter { it.sahkoposti != inviter.sahkoposti }) {
            val data =
                ApplicationNotificationEmail(
                    inviter.fullName(),
                    inviter.sahkoposti,
                    newContact.sahkoposti,
                    hakemusEntity.applicationType,
                    hakemusEntity.hanke.hankeTunnus,
                    hakemusEntity.hanke.nimi,
                )
            applicationEventPublisher.publishEvent(data)
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

    companion object {
        /** @return HankekayttajaIDs of new yhteyshenkilot that need to be added. */
        fun <H : YhteyshenkiloEntity> CustomerWithContactsRequest.toExistingYhteystietoEntity(
            yhteystietoEntity: YhteystietoEntity<H>
        ): Set<UUID> {
            if (customer.type != yhteystietoEntity.tyyppi && customer.registryKeyHidden) {
                // If new customer type doesn't match the old one, the type of registry key will be
                // wrong, but it will be retained if the key is hidden.
                // Validation only checks the new type.
                throw InvalidHiddenRegistryKey(
                    "New customer type doesn't match the old.",
                    customer.type,
                    yhteystietoEntity.tyyppi,
                )
            }
            yhteystietoEntity.tyyppi = customer.type
            yhteystietoEntity.nimi = customer.name
            yhteystietoEntity.sahkoposti = customer.email
            yhteystietoEntity.puhelinnumero = customer.phone
            if (!customer.registryKeyHidden) {
                yhteystietoEntity.registryKey = customer.registryKey
            }
            val newHenkilot = yhteystietoEntity.yhteyshenkilot.update(this.contacts)
            return newHenkilot
        }

        /** @return HankekayttajaIDs of new yhteyshenkilot that need to be added. */
        private fun <H : YhteyshenkiloEntity> MutableList<H>.update(
            contacts: List<ContactRequest>
        ): Set<UUID> {
            val existingIds = this.map { it.hankekayttaja.id }.toSet()
            val newIds = contacts.map { it.hankekayttajaId }.toSet()
            val toRemove = existingIds.minus(newIds)
            val toAdd = newIds.minus(existingIds)
            this.removeIf { toRemove.contains(it.hankekayttaja.id) }
            return toAdd
        }
    }
}

class IncompatibleHakemusUpdateRequestException(
    application: HakemusIdentifier,
    oldApplicationClass: KClass<out HakemusEntityData>,
    requestClass: KClass<out HakemusUpdateRequest>,
) :
    RuntimeException(
        "Invalid update request for hakemus. ${application.logString()}, type=$oldApplicationClass, requestType=$requestClass"
    )

class InvalidHakemusyhteystietoException(entityId: UUID?, newId: UUID?) :
    RuntimeException(
        "Invalid hakemusyhteystieto received when updating hakemus. " +
            "yhteystietoId=${entityId}, newId=$newId"
    )

class InvalidHiddenRegistryKey(message: String, newType: CustomerType, oldType: CustomerType?) :
    RuntimeException(
        "RegistryKeyHidden used in an incompatible way: $message New=$newType Old=$oldType"
    )

class InvalidHakemusyhteyshenkiloException(invalidHankeKayttajaIds: Set<UUID>) :
    RuntimeException(
        "Invalid hanke user/users received when updating hakemus. " +
            "invalidHankeKayttajaIds=$invalidHankeKayttajaIds"
    )

class UserNotInContactsException(application: HakemusIdentifier) :
    RuntimeException("Sending user is not a contact on the hakemus. ${application.logString()}")

class HakemusNotFoundException(id: Long) : RuntimeException("Hakemus not found with id $id")

class HakemusAlreadySentException(id: Long?, alluid: Int?, status: ApplicationStatus?) :
    RuntimeException("Hakemus is already sent to Allu, id=$id, alluId=$alluid, status=$status")

class HakemusAlreadyProcessingException(id: Long?, alluid: Int?) :
    RuntimeException("Hakemus is no longer pending in Allu, id=$id, alluId=$alluid")

class HakemusGeometryException(validationError: GeometriatDao.InvalidDetail) :
    RuntimeException(
        "Invalid geometry received when updating hakemus. " +
            "reason=${validationError.reason}, location=${validationError.location}"
    )

class HakemusGeometryNotInsideHankeException(message: String) : RuntimeException(message) {
    constructor(
        geometry: Polygon
    ) : this(
        "Hakemus geometry doesn't match any hankealue. " +
            "hakemus geometry=${geometry.toJsonString()}"
    )

    constructor(
        hankealueId: Int,
        geometry: Polygon,
    ) : this(
        "Hakemus geometry is outside the associated hankealue. " +
            "hankealue=$hankealueId, hakemus geometry=${geometry.toJsonString()}"
    )
}

class HakemusDecisionNotFoundException(message: String) : RuntimeException(message)

class HakemusNotYetInAlluException(hakemus: HakemusIdentifier) :
    RuntimeException("Hakemus is not yet in Allu. ${hakemus.logString()}")

class WrongHakemusTypeException(
    hakemus: HakemusIdentifier,
    type: ApplicationType,
    allowed: List<ApplicationType>,
) :
    RuntimeException(
        "Wrong application type for this action. type=$type, " +
            "allowed types=${allowed.joinToString(", ")}, ${hakemus.logString()}"
    )

class HakemusInWrongStatusException(
    hakemus: HakemusIdentifier,
    status: ApplicationStatus?,
    allowed: List<ApplicationStatus>,
) :
    RuntimeException(
        "Hakemus is in the wrong status for this operation. status=$status, " +
            "allowed statuses=${allowed.joinToString(", ")}, ${hakemus.logString()}, "
    )

class CompletionDateException(
    type: ValmistumisilmoitusType,
    error: String,
    date: LocalDate,
    hakemus: HakemusIdentifier,
) :
    RuntimeException(
        "Invalid date in ${type.logName} report. $error date=$date, ${hakemus.logString()}"
    )
