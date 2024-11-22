package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.InformationRequest
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hakemus.HakemusDataValidator
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusService.Companion.toExistingYhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.TaydennysLoggingService
import fi.hel.haitaton.hanke.logging.TaydennyspyyntoLoggingService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class TaydennysService(
    private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    private val hakemusService: HakemusService,
    private val taydennysRepository: TaydennysRepository,
    private val hakemusRepository: HakemusRepository,
    private val alluClient: AlluClient,
    private val loggingService: TaydennysLoggingService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val taydennysLoggingService: TaydennysLoggingService,
    private val taydennyspyyntoLoggingService: TaydennyspyyntoLoggingService,
    private val disclosureLogService: DisclosureLogService,
) {
    @Transactional(readOnly = true)
    fun findTaydennyspyynto(hakemusId: Long): Taydennyspyynto? =
        taydennyspyyntoRepository.findByApplicationId(hakemusId)?.toDomain()

    @Transactional(readOnly = true)
    fun findTaydennys(hakemusId: Long): Taydennys? =
        taydennysRepository.findByApplicationId(hakemusId)?.toDomain()

    @Transactional
    fun saveTaydennyspyyntoFromAllu(hakemus: HakemusIdentifier) {
        val request: InformationRequest? = alluClient.getInformationRequest(hakemus.alluid!!)
        if (request == null) {
            logger.error {
                "Couldn't find the information request from Allu. Ignoring the information request. ${hakemus.logString()}"
            }
            return
        }

        val entity =
            TaydennyspyyntoEntity(
                applicationId = hakemus.id,
                alluId = request.informationRequestId,
                kentat =
                    request.fields.associate { it.fieldKey to it.requestDescription }.toMutableMap(),
            )

        taydennyspyyntoRepository.save(entity)
    }

    @Transactional
    fun create(hakemusId: Long, currentUserId: String): Taydennys {
        val taydennyspyynto =
            taydennyspyyntoRepository.findByApplicationId(hakemusId)
                ?: throw NoTaydennyspyyntoException(hakemusId)
        val hakemus = hakemusRepository.getReferenceById(hakemusId)

        if (hakemus.alluStatus != ApplicationStatus.WAITING_INFORMATION) {
            throw HakemusInWrongStatusException(
                hakemus,
                hakemus.alluStatus,
                listOf(ApplicationStatus.WAITING_INFORMATION),
            )
        }

        val saved = createFromHakemus(hakemus, taydennyspyynto).toDomain()
        loggingService.logCreate(saved, currentUserId)
        return saved
    }

    private fun createFromHakemus(
        hakemus: HakemusEntity,
        taydennyspyynto: TaydennyspyyntoEntity,
    ): TaydennysEntity {
        val taydennys =
            taydennysRepository.save(
                TaydennysEntity(
                    taydennyspyynto = taydennyspyynto,
                    hakemusData = hakemus.hakemusEntityData,
                )
            )

        taydennys.yhteystiedot.putAll(
            hakemus.yhteystiedot.mapValues { createYhteystieto(it.value, taydennys) }
        )

        return taydennys
    }

    @Transactional
    fun removeTaydennyspyyntoIfItExists(application: HakemusEntity) {
        logger.info {
            "A hakemus has has entered handling. Checking if there's a täydennyspyyntö for the hakemus. ${application.logString()}"
        }

        taydennyspyyntoRepository.findByApplicationId(application.id)?.let {
            logger.info { "A täydennyspyyntö was found. Removing it." }
            taydennyspyyntoRepository.delete(it)
            taydennyspyyntoRepository.flush()

            if (application.alluStatus != ApplicationStatus.WAITING_INFORMATION) {
                logger.error {
                    "A hakemus moved to handling and it had a täydennyspyyntö, " +
                        "but the previous state was not 'WAITING_INFORMATION'. " +
                        "status=${application.alluStatus} ${application.logString()}"
                }
            }
        }
    }

    @Transactional
    fun sendTaydennys(id: UUID, currentUserId: String): Hakemus {
        val taydennysEntity =
            taydennysRepository.findByIdOrNull(id) ?: throw TaydennysNotFoundException(id)
        val taydennys = taydennysEntity.toDomain()
        val hakemus =
            hakemusRepository.getReferenceById(taydennysEntity.taydennyspyynto.applicationId)
        val hanke = hakemus.hanke

        logger.info {
            "Sending taydennys to Allu. " +
                "${taydennysEntity.logString()} " +
                "${hakemus.logString()} " +
                hanke.logString()
        }

        val changes =
            taydennys.hakemusData.listChanges(hakemus.toHakemus().applicationData).ifEmpty {
                throw NoChangesException(taydennysEntity, hakemus)
            }

        if (hakemus.alluStatus != ApplicationStatus.WAITING_INFORMATION) {
            throw HakemusInWrongStatusException(
                hakemus,
                hakemus.alluStatus,
                listOf(ApplicationStatus.WAITING_INFORMATION),
            )
        }

        HakemusDataValidator.ensureValidForSend(taydennys.hakemusData)

        if (!hanke.generated) {
            taydennysEntity.hakemusData.areas?.let { areas ->
                hakemusService.assertGeometryCompatibility(hanke.id, areas)
            }
        }

        logger.info("Sending täydennys id=$id")
        val taydennyspyyntoId = taydennysEntity.taydennyspyyntoAlluId()
        sendTaydennysToAllu(taydennys, hakemus, taydennyspyyntoId, hanke.hankeTunnus, changes)

        if (hanke.generated) {
            hanke.nimi = taydennys.hakemusData.name
        }

        logger.info {
            "Täydennys sent, updating hakemus identifier and status. " +
                "${taydennysEntity.logString()} ${hakemus.logString()}"
        }
        hakemusService.updateStatusFromAllu(hakemus)

        // TODO: Meld täydennys to hakemus and log the change

        taydennysLoggingService.logDelete(taydennys, currentUserId)
        taydennysRepository.delete(taydennysEntity)
        val taydennyspyynto = taydennysEntity.taydennyspyynto.toDomain()
        taydennyspyyntoLoggingService.logDelete(taydennyspyynto, currentUserId)
        taydennyspyyntoRepository.delete(taydennysEntity.taydennyspyynto)

        return hakemusRepository.save(hakemus).toHakemus()
    }

    @Transactional
    fun updateTaydennys(id: UUID, request: HakemusUpdateRequest, currentUserId: String): Taydennys {
        logger.info("Updating taydennys id=$id")

        val taydennysEntity =
            taydennysRepository.findByIdOrNull(id) ?: throw TaydennysNotFoundException(id)
        val originalTaydennys = taydennysEntity.toDomain()

        logger.info { "The taydennys to update is ${taydennysEntity.logString()}" }

        assertUpdateCompatible(taydennysEntity, request)

        if (!request.hasChanges(originalTaydennys.hakemusData)) {
            logger.info("Not updating unchanged hakemus data. ${taydennysEntity.id}")
            return originalTaydennys
        }

        hakemusService.assertGeometryValidity(request.areas)

        val hakemusEntity = hakemusRepository.getReferenceById(taydennysEntity.hakemusId())
        val hanke = hakemusEntity.hanke
        val yhteystiedot = taydennysEntity.yhteystiedot
        hakemusService.assertYhteystiedotValidity(hanke, yhteystiedot, request)
        hakemusService.assertOrUpdateHankealueet(hanke, request)

        val originalContactUserIds = taydennysEntity.allContactUsers().map { it.id }.toSet()
        val updatedTaydennysEntity = saveWithUpdate(taydennysEntity, request, hanke.id)
        sendHakemusNotifications(
            updatedTaydennysEntity,
            hakemusEntity,
            originalContactUserIds,
            currentUserId,
        )

        logger.info("Updated täydennys. ${updatedTaydennysEntity.logString()}")
        val updatedTaydennys = updatedTaydennysEntity.toDomain()
        taydennysLoggingService.logUpdate(originalTaydennys, updatedTaydennys, currentUserId)

        return updatedTaydennys
    }

    private fun assertUpdateCompatible(
        taydennysEntity: TaydennysEntity,
        request: HakemusUpdateRequest,
    ) {
        if (taydennysEntity.hakemusData.applicationType != request.applicationType) {
            throw IncompatibleTaydennysUpdateException(
                taydennysEntity,
                taydennysEntity.hakemusData.applicationType,
                request.applicationType,
            )
        }
    }

    /** Creates a new [TaydennysEntity] based on the given [request] and saves it. */
    private fun saveWithUpdate(
        taydennysEntity: TaydennysEntity,
        request: HakemusUpdateRequest,
        hankeId: Int,
    ): TaydennysEntity {
        logger.info { "Creating and saving new taydennys data. ${taydennysEntity.logString()}" }
        taydennysEntity.hakemusData = request.toEntityData(taydennysEntity.hakemusData)
        updateYhteystiedot(taydennysEntity, request.customersByRole(), hankeId)

        hakemusService.updateTormaystarkastelut(taydennysEntity.hakemusData)?.let {
            taydennysEntity.hakemusData = it
        }
        return taydennysRepository.save(taydennysEntity)
    }

    private fun updateYhteystiedot(
        taydennysEntity: TaydennysEntity,
        newYhteystiedot: Map<ApplicationContactType, CustomerWithContactsRequest?>,
        hankeId: Int,
    ) {
        ApplicationContactType.entries.forEach { rooli ->
            updateYhteystieto(rooli, taydennysEntity, newYhteystiedot[rooli], hankeId)?.let {
                taydennysEntity.yhteystiedot[rooli] = it
            } ?: taydennysEntity.yhteystiedot.remove(rooli)
        }
    }

    private fun updateYhteystieto(
        rooli: ApplicationContactType,
        taydennysEntity: TaydennysEntity,
        customerWithContactsRequest: CustomerWithContactsRequest?,
        hankeId: Int,
    ): TaydennysyhteystietoEntity? {
        if (customerWithContactsRequest == null) {
            // customer was deleted
            return null
        }
        return taydennysEntity.yhteystiedot[rooli]?.let {
            val newHenkilot = customerWithContactsRequest.toExistingYhteystietoEntity(it)
            newHenkilot.map { hankekayttajaId ->
                it.yhteyshenkilot.add(newTaydennysyhteyshenkiloEntity(hankekayttajaId, it, hankeId))
            }
            it
        }
            ?: customerWithContactsRequest.toNewTaydennysyhteystietoEntity(
                rooli,
                taydennysEntity,
                hankeId,
            )
    }

    private fun CustomerWithContactsRequest.toNewTaydennysyhteystietoEntity(
        rooli: ApplicationContactType,
        taydennysEntity: TaydennysEntity,
        hankeId: Int,
    ) =
        TaydennysyhteystietoEntity(
                tyyppi = customer.type,
                rooli = rooli,
                nimi = customer.name,
                sahkoposti = customer.email,
                puhelinnumero = customer.phone,
                registryKey = customer.registryKey,
                taydennys = taydennysEntity,
            )
            .apply {
                yhteyshenkilot.addAll(
                    contacts.map {
                        newTaydennysyhteyshenkiloEntity(it.hankekayttajaId, this, hankeId)
                    }
                )
            }

    private fun newTaydennysyhteyshenkiloEntity(
        hankekayttajaId: UUID,
        taydennysyhteystietoEntity: TaydennysyhteystietoEntity,
        hankeId: Int,
    ): TaydennysyhteyshenkiloEntity {
        return TaydennysyhteyshenkiloEntity(
            taydennysyhteystieto = taydennysyhteystietoEntity,
            hankekayttaja = hankeKayttajaService.getKayttajaForHanke(hankekayttajaId, hankeId),
            tilaaja = false,
        )
    }

    private fun sendHakemusNotifications(
        taydennysEntity: TaydennysEntity,
        hakemusEntity: HakemusEntity,
        excludedUserIds: Set<UUID>,
        userId: String,
    ) {
        val newContacts =
            taydennysEntity.allContactUsers().filterNot { excludedUserIds.contains(it.id) }
        if (newContacts.isNotEmpty()) {
            hakemusService.sendHakemusNotifications(newContacts, hakemusEntity, userId)
        }
    }

    private fun createYhteystieto(
        yhteystieto: HakemusyhteystietoEntity,
        taydennys: TaydennysEntity,
    ): TaydennysyhteystietoEntity =
        TaydennysyhteystietoEntity(
                tyyppi = yhteystieto.tyyppi,
                rooli = yhteystieto.rooli,
                nimi = yhteystieto.nimi,
                sahkoposti = yhteystieto.sahkoposti,
                puhelinnumero = yhteystieto.puhelinnumero,
                registryKey = yhteystieto.registryKey,
                taydennys = taydennys,
            )
            .apply {
                yhteyshenkilot.addAll(
                    yhteystieto.yhteyshenkilot.map { createYhteyshenkilo(it, this) }
                )
            }

    private fun createYhteyshenkilo(
        yhteyshenkilo: HakemusyhteyshenkiloEntity,
        yhteystieto: TaydennysyhteystietoEntity,
    ) =
        TaydennysyhteyshenkiloEntity(
            taydennysyhteystieto = yhteystieto,
            hankekayttaja = yhteyshenkilo.hankekayttaja,
            tilaaja = yhteyshenkilo.tilaaja,
        )

    private fun sendTaydennysToAllu(
        taydennys: Taydennys,
        hakemus: HakemusIdentifier,
        taydennyspyyntoAlluId: Int,
        hankeTunnus: String,
        muutokset: List<String>,
    ) {
        val updatedFieldKeys =
            muutokset.mapNotNull { InformationRequestFieldKey.fromHaitatonFieldName(it) }.toSet()

        val alluData = taydennys.hakemusData.toAlluData(hankeTunnus)
        disclosureLogService.withDisclosureLogging(hakemus.id, alluData) {
            alluClient.respondToInformationRequest(
                hakemus.alluid!!,
                taydennyspyyntoAlluId,
                alluData,
                updatedFieldKeys,
            )
        }
    }
}

class NoTaydennyspyyntoException(hakemusId: Long) :
    RuntimeException("Hakemus doesn't have an open täydennyspyyntö. hakemusId=$hakemusId")

class TaydennysNotFoundException(id: UUID) : RuntimeException("Täydennys not found. id=$id")

class IncompatibleTaydennysUpdateException(
    taydennysEntity: TaydennysEntity,
    existingType: ApplicationType,
    requestedType: ApplicationType,
) :
    RuntimeException(
        "Invalid update request for täydennys. ${taydennysEntity.id}, existing type=$existingType, requested type=$requestedType"
    )

class NoChangesException(taydennys: TaydennysIdentifier, hakemus: HakemusIdentifier) :
    RuntimeException(
        "Not sending a täydennys without any changes. ${taydennys.logString()} ${hakemus.logString()}"
    )
