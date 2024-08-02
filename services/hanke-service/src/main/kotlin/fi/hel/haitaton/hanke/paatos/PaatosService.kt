package fi.hel.haitaton.hanke.paatos

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

@Service
class PaatosService(
    private val paatosRepository: PaatosRepository,
    private val alluClient: AlluClient,
    private val fileClient: FileClient,
) {
    fun findByHakemusId(hakemusId: Long): List<Paatos> =
        paatosRepository.findByHakemusId(hakemusId).map { it.toDomain() }

    fun saveKaivuilmoituksenPaatos(hakemus: HakemusEntity, event: ApplicationStatusEvent) {
        val alluId = hakemus.alluid!!
        val pdfData = alluClient.getDecisionPdf(alluId)

        val filename = "${event.applicationIdentifier}-paatos.pdf"
        createPaatos(
            pdfData,
            filename,
            hakemus.id,
            alluId,
            event.applicationIdentifier,
            PaatosTyyppi.PAATOS,
        )
    }

    fun saveKaivuilmoituksenToiminnallinenKunto(
        hakemus: HakemusEntity,
        event: ApplicationStatusEvent
    ) {
        val alluId = hakemus.alluid!!
        val pdfData = alluClient.getOperationalConditionPdf(alluId)

        val filename = "${event.applicationIdentifier}-toiminnallinen-kunto.pdf"
        createPaatos(
            pdfData,
            filename,
            hakemus.id,
            alluId,
            event.applicationIdentifier,
            PaatosTyyppi.TOIMINNALLINEN_KUNTO,
        )
    }

    private fun createPaatos(
        pdfData: ByteArray,
        filename: String,
        hakemusId: Long,
        alluId: Int,
        hakemustunnus: String,
        tyyppi: PaatosTyyppi,
    ) {
        val applicationResponse = alluClient.getApplicationInformation(alluId)

        val path = uploadPaatos(pdfData, filename, hakemusId)

        paatosRepository.save(
            PaatosEntity(
                hakemusId = hakemusId,
                hakemustunnus = hakemustunnus,
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
