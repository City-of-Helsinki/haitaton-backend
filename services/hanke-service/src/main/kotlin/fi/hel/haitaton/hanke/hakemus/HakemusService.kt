package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ALLU_APPLICATION_ERROR_MSG
import fi.hel.haitaton.hanke.application.ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.ExcavationNotificationData
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HakemusLoggingService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.toJsonString
import java.util.UUID
import kotlin.reflect.KClass
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HakemusService(
    private val applicationRepository: ApplicationRepository,
    private val hankeRepository: HankeRepository,
    private val geometriatDao: GeometriatDao,
    private val hankealueService: HankealueService,
    private val hakemusLoggingService: HakemusLoggingService,
    private val disclosureLogService: DisclosureLogService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val attachmentService: ApplicationAttachmentService,
    private val alluClient: CableReportService,
) {

    @Transactional(readOnly = true)
    fun getById(applicationId: Long): Hakemus = getEntityById(applicationId).toHakemus()

    @Transactional(readOnly = true)
    fun hakemusResponse(applicationId: Long): HakemusResponse = getById(applicationId).toResponse()

    @Transactional(readOnly = true)
    fun hankkeenHakemuksetResponse(hankeTunnus: String): HankkeenHakemuksetResponse {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)
        return HankkeenHakemuksetResponse(
            hanke.hakemukset.map { hakemus -> HankkeenHakemusResponse(hakemus) }
        )
    }

    /** Create a johtoselvitys from a hanke that was just created. */
    @Transactional
    fun createJohtoselvitys(hanke: HankeEntity, currentUserId: String): Hakemus {
        val data =
            CableReportApplicationData(
                name = hanke.nimi,
                applicationType = ApplicationType.CABLE_REPORT,
                pendingOnClient = true,
                areas = null,
                customerWithContacts = null,
                contractorWithContacts = null,
                startTime = null,
                endTime = null,
                rockExcavation = null,
                workDescription = "",
            )
        val entity =
            ApplicationEntity(
                id = null,
                alluid = null,
                alluStatus = null,
                applicationIdentifier = null,
                userId = currentUserId,
                applicationType = ApplicationType.CABLE_REPORT,
                applicationData = data,
                hanke = hanke,
                yhteystiedot = mutableMapOf()
            )

        val hakemus = applicationRepository.save(entity).toHakemus()
        hakemusLoggingService.logCreate(hakemus, currentUserId)
        return hakemus
    }

    @Transactional
    fun updateHakemus(
        applicationId: Long,
        request: HakemusUpdateRequest,
        userId: String
    ): HakemusResponse {
        logger.info("Updating application id=$applicationId")

        val applicationEntity = getEntityById(applicationId)
        val hakemus = applicationEntity.toHakemus() // the original state for audit logging

        assertNotSent(applicationEntity)
        assertCompatibility(applicationEntity, request)

        if (!request.hasChanges(applicationEntity)) {
            logger.info(
                "Not updating unchanged application data. id=$applicationId, identifier=${applicationEntity.applicationIdentifier}"
            )
            return hakemus.toResponse()
        }

        assertGeometryValidity(request.areas) { validationError ->
            "Invalid geometry received when updating application id=${applicationEntity.id}, reason=${validationError.reason}, location=${validationError.location}"
        }

        assertYhteystiedotValidity(applicationEntity, request)

        val hankeEntity = applicationEntity.hanke
        if (!hankeEntity.generated) {
            request.areas?.let { areas ->
                assertGeometryCompatibility(hankeEntity.id, areas) { area ->
                    "Application geometry doesn't match any hankealue when updating application id=${applicationEntity.id}, " +
                        "hankeId=${hankeEntity.id}, application geometry=${area.geometry.toJsonString()}"
                }
            }
        } else {
            updateHankealueet(hankeEntity, request)
        }

        val updatedHakemus = saveWithUpdate(applicationEntity, request).toHakemus()

        logger.info("Updated application id=${applicationId}")
        hakemusLoggingService.logUpdate(hakemus, updatedHakemus, userId)

        return updatedHakemus.toResponse()
    }

    @Transactional
    fun sendHakemus(id: Long, currentUserId: String): Hakemus {
        val hakemus = getEntityById(id)

        setOrderedOnSend(hakemus, currentUserId)

        val hanke = hakemus.hanke
        if (!hanke.generated) {
            hakemus.applicationData.areas?.let { areas ->
                assertGeometryCompatibility(hanke.id, areas) { applicationArea ->
                    "Application geometry doesn't match any hankealue when sending application for user $currentUserId, " +
                        "${hanke.logString()}, hakemusId=${hakemus.id}, " +
                        "application geometry=${applicationArea.geometry.toJsonString()}"
                }
            }
        }

        assertNotSent(hakemus)

        // The application should no longer be a draft
        hakemus.applicationData = hakemus.applicationData.copy(pendingOnClient = false)

        logger.info("Sending hakemus id=$id")
        hakemus.alluid = createApplicationInAllu(hakemus.toHakemus())

        logger.info {
            "Application sent, fetching application identifier and status. id=$id, alluid=${hakemus.alluid}."
        }
        getApplicationInformationFromAllu(hakemus.alluid!!)?.let { response ->
            hakemus.applicationIdentifier = response.applicationId
            hakemus.alluStatus = response.status
        }

        logger.info(
            "Sent application id=$id, alluid=${hakemus.alluid}, alluStatus = ${hakemus.alluStatus}"
        )
        // Save only if sendApplicationToAllu didn't throw an exception
        return applicationRepository.save(hakemus).toHakemus()
    }

    /** Find the application entity or throw an exception. */
    private fun getEntityById(id: Long): ApplicationEntity =
        applicationRepository.findOneById(id) ?: throw ApplicationNotFoundException(id)

    private fun setOrderedOnSend(hakemus: ApplicationEntity, currentUserId: String) {
        val yhteyshenkilo: HakemusyhteyshenkiloEntity =
            listOf(
                    ApplicationContactType.HAKIJA,
                    ApplicationContactType.TYON_SUORITTAJA,
                    ApplicationContactType.RAKENNUTTAJA,
                    ApplicationContactType.ASIANHOITAJA
                )
                .asSequence()
                .mapNotNull { hakemus.yhteystiedot[it] }
                .flatMap { it.yhteyshenkilot }
                .find { it.hankekayttaja.permission?.userId == currentUserId }
                ?: throw UserNotInContactsException(hakemus.id!!, currentUserId)
        yhteyshenkilo.tilaaja = true
    }

    /** Assert that the application has not been sent to Allu. */
    private fun assertNotSent(applicationEntity: ApplicationEntity) {
        if (applicationEntity.alluid != null) {
            throw ApplicationAlreadySentException(
                applicationEntity.id,
                applicationEntity.alluid,
                applicationEntity.alluStatus
            )
        }
    }

    private fun getApplicationInformationFromAllu(alluid: Int): AlluApplicationResponse? {
        return try {
            alluClient.getApplicationInformation(alluid)
        } catch (e: Exception) {
            logger.error(e) { "Exception while getting application information." }
            null
        }
    }

    /** Creates new application in Allu. All attachments are sent after creation. */
    private fun createApplicationInAllu(hakemus: Hakemus): Int {
        HakemusDataValidator.ensureValidForSend(hakemus.applicationData)
        val alluId =
            createApplicationToAllu(hakemus.id, hakemus.hankeTunnus, hakemus.applicationData)
        try {
            attachmentService.sendInitialAttachments(alluId, hakemus.id)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while sending the initial attachments. Canceling the application. " +
                    "id=${hakemus.id}, alluid=$alluId"
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

        return withFormDataPdfUploading(hakemusData) {
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
    private fun withFormDataPdfUploading(cableReport: HakemusData, alluAction: () -> Int): Int {
        // TODO: Update PDF sending to handle Hakemusdata
        return alluAction()
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
                applicationId,
                alluApplicationData,
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
                alluApplicationData,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
            throw e
        }
    }

    /** Assert that the update request is compatible with the application data. */
    private fun assertCompatibility(
        applicationEntity: ApplicationEntity,
        request: HakemusUpdateRequest
    ) {
        val expected =
            when (applicationEntity.applicationData) {
                is CableReportApplicationData -> request is JohtoselvityshakemusUpdateRequest
                is ExcavationNotificationData -> request is KaivuilmoitusUpdateRequest
            }
        if (!expected) {
            throw IncompatibleHakemusUpdateRequestException(
                applicationEntity.id!!,
                applicationEntity.applicationData::class,
                request::class
            )
        }
    }

    /** Assert that the geometries are valid. */
    private fun assertGeometryValidity(
        areas: List<ApplicationArea>?,
        customMessageOnFailure: (GeometriatDao.InvalidDetail) -> String
    ) {
        if (areas != null) {
            geometriatDao.validateGeometriat(areas.map { it.geometry })?.let {
                throw ApplicationGeometryException(customMessageOnFailure(it))
            }
        }
    }

    /**
     * Assert that the customers match and that the contacts in the update request are hanke users
     * of the application hanke.
     */
    private fun assertYhteystiedotValidity(
        applicationEntity: ApplicationEntity,
        updateRequest: HakemusUpdateRequest
    ) {
        val customersWithContacts = updateRequest.customersByRole()
        ApplicationContactType.entries.forEach {
            assertYhteystietoValidity(
                applicationEntity.id!!,
                it,
                applicationEntity.yhteystiedot[it],
                customersWithContacts[it]
            )
        }

        assertYhteyshenkilotValidity(
            applicationEntity.hanke,
            customersWithContacts.values
                .filterNotNull()
                .flatMap { it.contacts.map { contact -> contact.hankekayttajaId } }
                .toSet()
        ) {
            "Invalid hanke user/users received when updating application id=${applicationEntity.id}, invalidHankeKayttajaIds=$it"
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
        applicationId: Long,
        rooli: ApplicationContactType,
        hakemusyhteystietoEntity: HakemusyhteystietoEntity?,
        customerWithContacts: CustomerWithContactsRequest?
    ) {
        if (
            customerWithContacts == null ||
                customerWithContacts.customer.yhteystietoId == null ||
                customerWithContacts.customer.yhteystietoId == hakemusyhteystietoEntity?.id
        ) {
            return
        }
        throw InvalidHakemusyhteystietoException(
            applicationId,
            rooli,
            hakemusyhteystietoEntity?.id,
            customerWithContacts.customer.yhteystietoId
        )
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
                customMessageOnFailure(newInvalidHankekayttajaIds)
            )
        }
    }

    /** Assert that the geometries are compatible with the hanke area geometries. */
    private fun assertGeometryCompatibility(
        hankeId: Int,
        areas: List<ApplicationArea>,
        customMessageOnFailure: (ApplicationArea) -> String
    ) {
        areas.forEach { area ->
            if (!geometriatDao.isInsideHankeAlueet(hankeId, area.geometry))
                throw ApplicationGeometryNotInsideHankeException(customMessageOnFailure(area))
        }
    }

    /** Update the hanke areas based on the update request work areas. */
    private fun updateHankealueet(hankeEntity: HankeEntity, updateRequest: HakemusUpdateRequest) {
        val hankealueet =
            HankealueService.createHankealueetFromApplicationAreas(
                updateRequest.areas,
                updateRequest.startTime,
                updateRequest.endTime
            )
        hankeEntity.alueet.clear()
        hankeEntity.alueet.addAll(
            hankealueService.createAlueetFromCreateRequest(hankealueet, hankeEntity)
        )
    }

    /** Creates a new [ApplicationEntity] based on the given [request] and saves it. */
    private fun saveWithUpdate(
        applicationEntity: ApplicationEntity,
        request: HakemusUpdateRequest
    ): ApplicationEntity {
        val updatedApplicationEntity =
            applicationEntity.copy(
                applicationData = request.toApplicationData(applicationEntity.applicationData),
                yhteystiedot =
                    updateYhteystiedot(
                        applicationEntity,
                        applicationEntity.yhteystiedot,
                        request.customersByRole()
                    )
            )
        return applicationRepository.save(updatedApplicationEntity)
    }

    private fun updateYhteystiedot(
        applicationEntity: ApplicationEntity,
        currentYhteystiedot: Map<ApplicationContactType, HakemusyhteystietoEntity>,
        newYhteystiedot: Map<ApplicationContactType, CustomerWithContactsRequest?>
    ): MutableMap<ApplicationContactType, HakemusyhteystietoEntity> {
        val updatedYhteystiedot = mutableMapOf<ApplicationContactType, HakemusyhteystietoEntity>()
        ApplicationContactType.entries.forEach { rooli ->
            updateYhteystieto(
                    rooli,
                    applicationEntity,
                    currentYhteystiedot[rooli],
                    newYhteystiedot[rooli]
                )
                ?.let { updatedYhteystiedot[rooli] = it }
        }
        return updatedYhteystiedot
    }

    private fun updateYhteystieto(
        rooli: ApplicationContactType,
        applicationEntity: ApplicationEntity,
        hakemusyhteystietoEntity: HakemusyhteystietoEntity?,
        customerWithContactsRequest: CustomerWithContactsRequest?
    ): HakemusyhteystietoEntity? {
        if (customerWithContactsRequest == null) {
            // customer was deleted
            return null
        }
        if (hakemusyhteystietoEntity == null) {
            // new customer was added
            return customerWithContactsRequest.toNewHakemusyhteystietoEntity(
                rooli,
                applicationEntity
            )
        }
        // update existing customer
        return customerWithContactsRequest.toExistingHakemusyhteystietoEntity(
            hakemusyhteystietoEntity
        )
    }

    private fun CustomerWithContactsRequest.toNewHakemusyhteystietoEntity(
        rooli: ApplicationContactType,
        applicationEntity: ApplicationEntity
    ) =
        HakemusyhteystietoEntity(
                tyyppi = customer.type,
                rooli = rooli,
                nimi = customer.name,
                sahkoposti = customer.email,
                puhelinnumero = customer.phone,
                ytunnus = customer.registryKey,
                application = applicationEntity,
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
                    hankekayttajaId,
                    hakemusyhteystietoEntity.application.hanke.id
                ),
            tilaaja = false
        )

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
            }
        )
    }
}

class IncompatibleHakemusUpdateRequestException(
    applicationId: Long,
    oldApplicationClass: KClass<out ApplicationData>,
    requestClass: KClass<out HakemusUpdateRequest>,
) :
    RuntimeException(
        "Invalid update request for application id=$applicationId type=$oldApplicationClass requestType=$requestClass"
    )

class InvalidHakemusyhteystietoException(
    applicationId: Long,
    rooli: ApplicationContactType,
    yhteystietoId: UUID?,
    newId: UUID?,
) :
    RuntimeException(
        "Invalid hakemusyhteystieto received when updating application id=$applicationId, role=$rooli, yhteystietoId=$yhteystietoId, newId=$newId"
    )

class InvalidHakemusyhteyshenkiloException(message: String) : RuntimeException(message)

class UserNotInContactsException(applicationId: Long, userId: String) :
    RuntimeException(
        "Sending user is not a contact on the application applicationId=$applicationId, userId=$userId"
    )
