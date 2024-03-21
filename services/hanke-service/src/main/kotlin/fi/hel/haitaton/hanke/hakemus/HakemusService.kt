package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
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
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.HakemusLoggingService
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
    private val hankeKayttajaService: HankeKayttajaService,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    @Transactional(readOnly = true)
    fun hakemusResponse(applicationId: Long): HakemusResponse {
        val applicationEntity =
            applicationRepository.findOneById(applicationId)
                ?: throw ApplicationNotFoundException(applicationId)
        return hakemusResponseWithYhteystiedot(applicationEntity)
    }

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

        val applicationEntity =
            applicationRepository.findOneById(applicationId)
                ?: throw ApplicationNotFoundException(applicationId)
        val hakemus = applicationEntity.toHakemus() // the original state for audit logging

        assertNotSent(applicationEntity)
        assertCompatibility(applicationEntity, request)

        if (!request.hasChanges(applicationEntity)) {
            logger.info(
                "Not updating unchanged application data. id=$applicationId, identifier=${applicationEntity.applicationIdentifier}"
            )
            return hakemusResponseWithYhteystiedot(applicationEntity)
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

        val updatedApplicationEntity = saveWithUpdate(applicationEntity, request)

        logger.info("Updated application id=${applicationId}")
        hakemusLoggingService.logUpdate(hakemus, updatedApplicationEntity.toHakemus(), userId)

        return hakemusResponseWithYhteystiedot(updatedApplicationEntity)
    }

    /** Assert that the application has not been sent to Allu. */
    private fun assertNotSent(applicationEntity: ApplicationEntity) {
        if (applicationEntity.alluid != null) {
            throw ApplicationAlreadySentException(applicationEntity.id, applicationEntity.alluid)
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

    private fun hakemusResponseWithYhteystiedot(applicationEntity: ApplicationEntity) =
        HakemusResponse(
            applicationEntity.id!!,
            applicationEntity.alluid,
            applicationEntity.alluStatus,
            applicationEntity.applicationIdentifier,
            applicationEntity.applicationType,
            hakemusDataResponseWithYhteystiedot(
                applicationEntity.applicationData,
                applicationEntity.yhteystiedot
            ),
            applicationEntity.hanke.hankeTunnus
        )

    private fun hakemusDataResponseWithYhteystiedot(
        applicationData: ApplicationData,
        hakemusyhteystiedot: Map<ApplicationContactType, HakemusyhteystietoEntity>
    ) =
        when (applicationData) {
            is CableReportApplicationData ->
                JohtoselvitysHakemusDataResponse(
                    applicationData.applicationType,
                    applicationData.pendingOnClient,
                    applicationData.name,
                    applicationData.postalAddress,
                    applicationData.constructionWork,
                    applicationData.maintenanceWork,
                    applicationData.propertyConnectivity,
                    applicationData.emergencyWork,
                    applicationData.rockExcavation,
                    applicationData.workDescription,
                    applicationData.startTime,
                    applicationData.endTime,
                    applicationData.areas ?: listOf(),
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.HAKIJA]
                    ),
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.TYON_SUORITTAJA]
                    ),
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.RAKENNUTTAJA]
                    ),
                    customerWithContactsResponseWithYhteystiedot(
                        hakemusyhteystiedot[ApplicationContactType.ASIANHOITAJA]
                    ),
                )
        }

    private fun customerWithContactsResponseWithYhteystiedot(
        hakemusyhteystieto: HakemusyhteystietoEntity?
    ): CustomerWithContactsResponse? =
        hakemusyhteystieto?.let {
            val customer = it.toCustomerResponse()
            val contacts =
                it.yhteyshenkilot.map { yhteyshenkilo -> yhteyshenkilo.toContactResponse() }
            CustomerWithContactsResponse(customer, contacts)
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
            .also { hakemusyhteystietoRepository.save(it) }
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
            .also { hakemusyhteyshenkiloRepository.save(it) }

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
