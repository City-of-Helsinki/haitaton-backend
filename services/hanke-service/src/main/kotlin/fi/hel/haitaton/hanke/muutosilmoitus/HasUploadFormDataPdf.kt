package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentMetadataWithType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import fi.hel.haitaton.hanke.hakemus.HakemusService
import java.time.LocalDateTime
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Interface for sharing `uploadFormDataPdf` between services without passing dependencies and
 * static names as parameters.
 */
interface HasUploadFormDataPdf {
    val alluClient: AlluClient
    val hakemusAttachmentService: ApplicationAttachmentService
    val hakemusService: HakemusService

    val entityNameForLogs: String
    val formDataPdfFilename: String

    fun formDataDescription(now: LocalDateTime): String

    fun uploadFormDataPdf(
        hakemus: HakemusIdentifier,
        hankeTunnus: String,
        data: HakemusData,
        extraAttachments: List<AttachmentMetadataWithType> = listOf(),
    ) {
        val formAttachment = createPdfFromHakemusData(hakemus, hankeTunnus, data, extraAttachments)
        try {
            alluClient.addAttachment(hakemus.alluid!!, formAttachment)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while uploading form data PDF attachment for $entityNameForLogs. Continuing anyway. ${hakemus.logString()}"
            }
        }
    }

    private fun createPdfFromHakemusData(
        hakemus: HakemusIdentifier,
        hankeTunnus: String,
        data: HakemusData,
        extraAttachments: List<AttachmentMetadataWithType>,
    ): Attachment {
        logger.info { "Creating a PDF from the hakemus data for data attachment." }
        val attachments = hakemusAttachmentService.getMetadataList(hakemus.id) + extraAttachments
        return hakemusService.getApplicationDataAsPdf(
            hankeTunnus,
            attachments,
            data,
            formDataPdfFilename,
            formDataDescription(LocalDateTime.now()),
        )
    }
}
