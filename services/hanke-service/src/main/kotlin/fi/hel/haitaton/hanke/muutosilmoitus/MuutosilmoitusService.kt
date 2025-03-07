package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hakemus.HakemusDataValidator
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusService.Companion.toExistingYhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.PaperDecisionReceiver
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.MuutosilmoitusLoggingService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.taydennys.NoChangesException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

const val FORM_DATA_PDF_FILENAME = "haitaton-form-data-muutosilmoitus.pdf"
const val HHS_PDF_FILENAME = "haitaton-haittojenhallintasuunnitelma-muutosilmoitus.pdf"
const val PAPER_DECISION_MSG =
    "Asiakas haluaa korvaavan päätöksen myös paperisena. Liitteessä " +
        "$FORM_DATA_PDF_FILENAME on päätöksen toimitukseen liittyvät osoitetiedot."

@Service
class MuutosilmoitusService(
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val hakemusRepository: HakemusRepository,
    private val loggingService: MuutosilmoitusLoggingService,
    override val hakemusService: HakemusService,
    override val hakemusAttachmentService: ApplicationAttachmentService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val disclosureLogService: DisclosureLogService,
    override val alluClient: AlluClient,
) : HasUploadFormDataPdf, HasUploadHaittojenhallintasuunnitelmaPdf {
    override val entityNameForLogs: String = "muutosilmoitus"
    override val formDataPdfFilename: String = FORM_DATA_PDF_FILENAME
    override val hhsPdfFilename: String = HHS_PDF_FILENAME

    override fun formDataDescription(now: LocalDateTime): String =
        "Muutosilmoitus form data from Haitaton, dated $now."

    override fun hhsDescription(now: LocalDateTime): String =
        "Haittojenhallintasuunnitelma from Haitaton muutosilmoitus, dated $now."

    @Transactional(readOnly = true)
    fun find(hakemusId: Long): Muutosilmoitus? =
        muutosilmoitusRepository.findByHakemusId(hakemusId)?.toDomain()

    @Transactional
    fun create(id: Long, currentUserId: String): Muutosilmoitus {
        val hakemus = hakemusRepository.findOneById(id) ?: throw HakemusNotFoundException(id)

        logger.info { "Creating a muutosilmoitus. ${hakemus.logString()}" }

        val allowedStatuses =
            listOf(ApplicationStatus.DECISION, ApplicationStatus.OPERATIONAL_CONDITION)
        if (hakemus.alluStatus !in allowedStatuses) {
            throw HakemusInWrongStatusException(hakemus, hakemus.alluStatus, allowedStatuses)
        }
        if (hakemus.applicationType != ApplicationType.EXCAVATION_NOTIFICATION) {
            throw WrongHakemusTypeException(
                hakemus,
                hakemus.applicationType,
                listOf(ApplicationType.EXCAVATION_NOTIFICATION),
            )
        }

        val saved = createFromHakemus(hakemus).toDomain()
        loggingService.logCreate(saved, currentUserId)
        return saved
    }

    @Transactional
    fun update(id: UUID, request: HakemusUpdateRequest, currentUserId: String): Muutosilmoitus {
        logger.info("Updating muutosilmoitus id=$id")

        val entity =
            muutosilmoitusRepository.findByIdOrNull(id) ?: throw MuutosilmoitusNotFoundException(id)
        if (entity.sent != null) throw MuutosilmoitusAlreadySentException(entity)
        val originalMuutosilmoitus = entity.toDomain()

        logger.info { "The muutosilmoitus to update is ${entity.logString()}" }

        assertUpdateCompatible(entity, request)

        if (!request.hasChanges(originalMuutosilmoitus.hakemusData)) {
            logger.info("Not updating unchanged hakemus data. ${entity.id}")
            return originalMuutosilmoitus
        }

        hakemusService.assertGeometryValidity(request.areas)

        val hakemusEntity = hakemusRepository.getReferenceById(entity.hakemusId)
        hakemusService.assertYhteystiedotValidity(hakemusEntity.hanke, entity.yhteystiedot, request)
        hakemusService.assertOrUpdateHankealueet(hakemusEntity.hanke, request)

        val originalContactUserIds = entity.allContactUsers().map { it.id }.toSet()
        val updatedMuutosilmoitusEntity = saveWithUpdate(entity, request, hakemusEntity.hanke.id)
        hakemusService.sendHakemusNotifications(
            updatedMuutosilmoitusEntity,
            hakemusEntity,
            originalContactUserIds,
            currentUserId,
        )

        logger.info("Updated muutosilmoitus. ${updatedMuutosilmoitusEntity.logString()}")
        val updatedMuutosilmoitus = updatedMuutosilmoitusEntity.toDomain()
        loggingService.logUpdate(originalMuutosilmoitus, updatedMuutosilmoitus, currentUserId)

        return updatedMuutosilmoitus
    }

    @Transactional
    fun delete(id: UUID, currentUserId: String) {
        val muutosilmoitusEntity =
            muutosilmoitusRepository.findByIdOrNull(id) ?: throw MuutosilmoitusNotFoundException(id)

        if (muutosilmoitusEntity.sent != null) {
            throw MuutosilmoitusAlreadySentException(muutosilmoitusEntity)
        }

        hakemusService.resetAreasIfHankeGenerated(
            muutosilmoitusEntity.hakemusId,
            muutosilmoitusEntity,
        )

        val muutosilmoitus = muutosilmoitusEntity.toDomain()
        muutosilmoitusRepository.delete(muutosilmoitusEntity)
        loggingService.logDelete(muutosilmoitus, currentUserId)
    }

    @Transactional
    fun send(id: UUID, paperDecisionReceiver: PaperDecisionReceiver?, currentUserId: String) {
        val entity =
            muutosilmoitusRepository.findByIdOrNull(id) ?: throw MuutosilmoitusNotFoundException(id)

        if (entity.sent != null) throw MuutosilmoitusAlreadySentException(entity)

        val muutosilmoitusBefore = entity.toDomain()
        entity.hakemusData = entity.hakemusData.copy(paperDecisionReceiver = paperDecisionReceiver)
        val muutosilmoitus = entity.toDomain()
        loggingService.logUpdate(muutosilmoitusBefore, muutosilmoitus, currentUserId)

        val hakemus = hakemusRepository.getReferenceById(entity.hakemusId)
        val hanke = hakemus.hanke

        logger.info(
            "Sending muutosilmoitus to Allu ${muutosilmoitus.logString()} " +
                "${hakemus.logString()} ${hanke.logString()}"
        )

        val changes =
            muutosilmoitus.hakemusData.listChanges(hakemus.toHakemus().applicationData).ifEmpty {
                throw NoChangesException(entityNameForLogs, entity, hakemus)
            }

        HakemusDataValidator.ensureValidForSend(muutosilmoitus.hakemusData)

        if (!hanke.generated) {
            entity.hakemusData.areas?.let { areas ->
                hakemusService.assertGeometryCompatibility(hanke.id, areas)
            }
        }

        logger.info("Sending muutosilmoitus id=$id")
        sendMuutosilmoitusToAllu(muutosilmoitus, hakemus, hanke, changes)

        entity.sent = OffsetDateTime.now()

        logger.info("Muutosilmoitus sent. ${entity.logString()} ${hakemus.logString()}")
    }

    private fun assertUpdateCompatible(
        entity: MuutosilmoitusEntity,
        request: HakemusUpdateRequest,
    ) {
        if (entity.hakemusData.applicationType != request.applicationType) {
            throw IncompatibleMuutosilmoitusUpdateException(
                entity,
                entity.hakemusData.applicationType,
                request.applicationType,
            )
        }
    }

    /** Creates a new [MuutosilmoitusEntity] based on the given [request] and saves it. */
    private fun saveWithUpdate(
        muutosilmoitusEntity: MuutosilmoitusEntity,
        request: HakemusUpdateRequest,
        hankeId: Int,
    ): MuutosilmoitusEntity {
        logger.info {
            "Creating and saving new muutosilmoitus data. ${muutosilmoitusEntity.logString()}"
        }
        muutosilmoitusEntity.hakemusData = request.toEntityData(muutosilmoitusEntity.hakemusData)
        updateYhteystiedot(muutosilmoitusEntity, request.customersByRole(), hankeId)

        hakemusService.updateTormaystarkastelut(muutosilmoitusEntity.hakemusData)?.let {
            muutosilmoitusEntity.hakemusData = it
        }
        return muutosilmoitusRepository.save(muutosilmoitusEntity)
    }

    private fun updateYhteystiedot(
        muutosilmoitusEntity: MuutosilmoitusEntity,
        newYhteystiedot: Map<ApplicationContactType, CustomerWithContactsRequest?>,
        hankeId: Int,
    ) {
        ApplicationContactType.entries.forEach { rooli ->
            updateYhteystieto(rooli, muutosilmoitusEntity, newYhteystiedot[rooli], hankeId)?.let {
                muutosilmoitusEntity.yhteystiedot[rooli] = it
            } ?: muutosilmoitusEntity.yhteystiedot.remove(rooli)
        }
    }

    private fun updateYhteystieto(
        rooli: ApplicationContactType,
        muutosilmoitusEntity: MuutosilmoitusEntity,
        customerWithContactsRequest: CustomerWithContactsRequest?,
        hankeId: Int,
    ): MuutosilmoituksenYhteystietoEntity? {
        if (customerWithContactsRequest == null) {
            // customer was deleted
            return null
        }
        return muutosilmoitusEntity.yhteystiedot[rooli]?.let {
            val newHenkilot = customerWithContactsRequest.toExistingYhteystietoEntity(it)
            newHenkilot.map { hankekayttajaId ->
                it.yhteyshenkilot.add(
                    newMuutosilmoituksenYhteyshenkiloEntity(hankekayttajaId, it, hankeId)
                )
            }
            it
        }
            ?: customerWithContactsRequest.toNewMuutosilmoituksenYhteystietoEntity(
                rooli,
                muutosilmoitusEntity,
                hankeId,
            )
    }

    private fun CustomerWithContactsRequest.toNewMuutosilmoituksenYhteystietoEntity(
        rooli: ApplicationContactType,
        muutosilmoitusEntity: MuutosilmoitusEntity,
        hankeId: Int,
    ) =
        MuutosilmoituksenYhteystietoEntity(
                tyyppi = customer.type,
                rooli = rooli,
                nimi = customer.name,
                sahkoposti = customer.email,
                puhelinnumero = customer.phone,
                registryKey = customer.registryKey,
                muutosilmoitus = muutosilmoitusEntity,
            )
            .apply {
                yhteyshenkilot.addAll(
                    contacts.map {
                        newMuutosilmoituksenYhteyshenkiloEntity(it.hankekayttajaId, this, hankeId)
                    }
                )
            }

    private fun newMuutosilmoituksenYhteyshenkiloEntity(
        hankekayttajaId: UUID,
        yhteystietoEntity: MuutosilmoituksenYhteystietoEntity,
        hankeId: Int,
    ): MuutosilmoituksenYhteyshenkiloEntity {
        return MuutosilmoituksenYhteyshenkiloEntity(
            yhteystieto = yhteystietoEntity,
            hankekayttaja = hankeKayttajaService.getKayttajaForHanke(hankekayttajaId, hankeId),
            tilaaja = false,
        )
    }

    private fun createFromHakemus(hakemus: HakemusEntity): MuutosilmoitusEntity {
        val muutosilmoitus =
            muutosilmoitusRepository.save(
                MuutosilmoitusEntity(
                    hakemusId = hakemus.id,
                    sent = null,
                    hakemusData = hakemus.hakemusEntityData,
                )
            )

        muutosilmoitus.yhteystiedot.putAll(
            hakemus.yhteystiedot.mapValues { createYhteystieto(it.value, muutosilmoitus) }
        )

        return muutosilmoitus
    }

    private fun sendMuutosilmoitusToAllu(
        muutosilmoitus: Muutosilmoitus,
        hakemus: HakemusIdentifier,
        hanke: HankeEntity,
        muutokset: List<String>,
    ) {
        val updatedFieldKeys =
            InformationRequestFieldKey.fromHaitatonFieldNames(
                muutokset,
                muutosilmoitus.hakemusData.applicationType,
            )
        val alluData = muutosilmoitus.hakemusData.toAlluData(hanke.hankeTunnus)

        disclosureLogService.withDisclosureLogging(hakemus.id, alluData) {
            alluClient.reportChange(hakemus.alluid!!, alluData, updatedFieldKeys)
        }
        uploadFormDataPdf(hakemus, hanke.hankeTunnus, muutosilmoitus.hakemusData)
        uploadHaittojenhallintasuunnitelmaPdf(hakemus, hanke, muutosilmoitus.hakemusData)

        if (muutosilmoitus.hakemusData.paperDecisionReceiver != null) {
            alluClient.sendSystemComment(hakemus.alluid!!, PAPER_DECISION_MSG)
        }
    }

    companion object {
        private fun createYhteystieto(
            yhteystieto: HakemusyhteystietoEntity,
            muutosilmoitus: MuutosilmoitusEntity,
        ): MuutosilmoituksenYhteystietoEntity =
            MuutosilmoituksenYhteystietoEntity(
                    tyyppi = yhteystieto.tyyppi,
                    rooli = yhteystieto.rooli,
                    nimi = yhteystieto.nimi,
                    sahkoposti = yhteystieto.sahkoposti,
                    puhelinnumero = yhteystieto.puhelinnumero,
                    registryKey = yhteystieto.registryKey,
                    muutosilmoitus = muutosilmoitus,
                )
                .apply {
                    yhteyshenkilot.addAll(
                        yhteystieto.yhteyshenkilot.map { createYhteyshenkilo(it, this) }
                    )
                }

        private fun createYhteyshenkilo(
            yhteyshenkilo: HakemusyhteyshenkiloEntity,
            yhteystieto: MuutosilmoituksenYhteystietoEntity,
        ) =
            MuutosilmoituksenYhteyshenkiloEntity(
                yhteystieto = yhteystieto,
                hankekayttaja = yhteyshenkilo.hankekayttaja,
                tilaaja = yhteyshenkilo.tilaaja,
            )
    }
}

class MuutosilmoitusNotFoundException(id: UUID) :
    RuntimeException("Muutosilmoitus not found. id=$id")

class MuutosilmoitusAlreadySentException(muutosilmoitus: MuutosilmoitusIdentifier) :
    RuntimeException("Muutosilmoitus is already sent to Allu. ${muutosilmoitus.logString()}")

class IncompatibleMuutosilmoitusUpdateException(
    entity: MuutosilmoitusEntity,
    existingType: ApplicationType,
    requestedType: ApplicationType,
) :
    RuntimeException(
        "Invalid update request for muutosilmoitus. existing type=$existingType, requested type=$requestedType. ${entity.logString()}"
    )
