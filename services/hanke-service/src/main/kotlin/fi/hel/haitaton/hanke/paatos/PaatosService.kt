package fi.hel.haitaton.hanke.paatos

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class PaatosService(
    private val paatosRepository: PaatosRepository,
    private val alluClient: AlluClient,
    private val fileClient: FileClient,
) {
    @Transactional(readOnly = true)
    fun findById(id: UUID): Paatos =
        paatosRepository.findByIdOrNull(id)?.toDomain() ?: throw PaatosNotFoundException(id)

    @Transactional(readOnly = true)
    fun findByHakemusId(hakemusId: Long): List<Paatos> =
        paatosRepository.findByHakemusId(hakemusId).map { it.toDomain() }

    @Transactional(readOnly = true)
    fun findByHakemusIds(hakemusIds: Collection<Long>): List<Paatos> =
        paatosRepository.findByHakemusIdIn(hakemusIds).map { it.toDomain() }

    @Transactional
    fun markReplaced(hakemustunnus: String) {
        paatosRepository.markReplacedByHakemustunnus(hakemustunnus)
    }

    fun downloadDecision(paatos: Paatos): Pair<String, ByteArray> {
        val bytes = fileClient.download(Container.PAATOKSET, paatos.blobLocation).content.toBytes()
        val filenameSuffix = paatos.tyyppi.name.lowercase().replace('_', '-')
        val filename = "${paatos.hakemustunnus}-$filenameSuffix.pdf"
        return Pair(filename, bytes)
    }

    @Transactional(readOnly = true)
    fun isRevertedToDecision(
        hakemus: HakemusIdentifier,
        applicationStatus: ApplicationStatus,
    ): Boolean =
        paatosRepository.existsByHakemustunnusAndTyyppi(
            hakemus.applicationIdentifier!!,
            PaatosTyyppi.valueOfApplicationStatus(applicationStatus),
        )

    @Transactional
    fun saveKaivuilmoituksenPaatos(hakemus: HakemusIdentifier, event: ApplicationStatusEvent) {
        val alluId = hakemus.alluid!!
        val pdfData = alluClient.getDecisionPdf(alluId)

        val filename = "${event.applicationIdentifier}-paatos.pdf"
        createPaatos(pdfData, filename, hakemus, PaatosTyyppi.PAATOS)
    }

    @Transactional
    fun saveKaivuilmoituksenToiminnallinenKunto(
        hakemus: HakemusIdentifier,
        event: ApplicationStatusEvent,
    ) {
        val alluId = hakemus.alluid!!
        val pdfData = alluClient.getOperationalConditionPdf(alluId)

        val filename = "${event.applicationIdentifier}-toiminnallinen-kunto.pdf"
        createPaatos(pdfData, filename, hakemus, PaatosTyyppi.TOIMINNALLINEN_KUNTO)
    }

    @Transactional
    fun saveKaivuilmoituksenTyoValmis(hakemus: HakemusIdentifier, event: ApplicationStatusEvent) {
        val alluId = hakemus.alluid!!
        val pdfData = alluClient.getWorkFinishedPdf(alluId)

        val filename = "${event.applicationIdentifier}-tyo-valmis.pdf"
        createPaatos(pdfData, filename, hakemus, PaatosTyyppi.TYO_VALMIS)
    }

    private fun createPaatos(
        pdfData: ByteArray,
        filename: String,
        hakemus: HakemusIdentifier,
        tyyppi: PaatosTyyppi,
    ) {
        logger.info { "Getting current hakemus name and dates from Allu. ${hakemus.logString()}" }
        val applicationResponse = alluClient.getApplicationInformation(hakemus.alluid!!)

        logger.info {
            "Uploading a new document for hakemus. ${hakemus.logString()}, " +
                "tyyppi = $tyyppi, tila = ${PaatosTila.NYKYINEN}"
        }
        val path = uploadPaatos(pdfData, filename, hakemus.id)

        logger.info {
            "Saving the uploaded document info and the current hakemus info to paatos table. ${hakemus.logString()}"
        }
        paatosRepository.save(
            PaatosEntity(
                hakemusId = hakemus.id,
                hakemustunnus = hakemus.applicationIdentifier!!,
                tyyppi = tyyppi,
                tila = PaatosTila.NYKYINEN,
                nimi = applicationResponse.name,
                alkupaiva = applicationResponse.startTime.toLocalDate(),
                loppupaiva = applicationResponse.endTime.toLocalDate(),
                blobLocation = path,
                size = pdfData.size,
            )
        )
    }

    private fun uploadPaatos(pdfData: ByteArray, filename: String, hakemusId: Long): String {
        val path = ApplicationAttachmentContentService.generateBlobPath(hakemusId)
        fileClient.upload(Container.PAATOKSET, path, filename, MediaType.APPLICATION_PDF, pdfData)
        return path
    }
}
