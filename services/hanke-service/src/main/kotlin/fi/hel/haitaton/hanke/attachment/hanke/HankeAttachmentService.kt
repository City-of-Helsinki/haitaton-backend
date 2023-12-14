package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import fi.hel.haitaton.hanke.attachment.common.validNameAndType
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentService(
    private val metadataService: HankeAttachmentMetadataService,
    private val contentService: HankeAttachmentContentService,
    private val scanClient: FileScanClient,
) {
    fun getMetadataList(hankeTunnus: String) = metadataService.getMetadataList(hankeTunnus)

    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = metadataService.findAttachment(attachmentId)
        val content = contentService.find(attachment)
        return AttachmentContent(attachment.fileName, attachment.contentType, content)
    }

    fun uploadHankeAttachment(
        hankeTunnus: String,
        attachment: MultipartFile,
    ): HankeAttachmentMetadataDto {
        val hanke = metadataService.hankeWithRoomForAttachment(hankeTunnus)

        val (filename, mediatype) = attachment.validNameAndType()

        scanAttachment(filename, attachment.bytes)

        val blobPath =
            contentService.upload(
                fileName = filename,
                contentType = mediatype,
                content = attachment.bytes,
                hankeId = hanke.id,
            )

        return metadataService
            .saveAttachment(
                hankeTunnus = hanke.hankeTunnus,
                name = filename,
                type = mediatype.toString(),
                blobPath = blobPath,
            )
            .also { logger.info { "Added attachment ${it.id} to hanke ${hanke.hankeTunnus}" } }
    }

    fun deleteAttachment(attachmentId: UUID) {
        logger.info { "Deleting hanke attachment $attachmentId..." }
        val attachmentToDelete = metadataService.findAttachment(attachmentId)
        contentService.delete(attachmentToDelete)
        metadataService.delete(attachmentId)
    }

    fun deleteAllAttachments(hanke: HankeIdentifier) {
        logger.info { "Deleting all attachments from hanke ${hanke.logString()}" }
        contentService.deleteAllForHanke(hanke.id)
        metadataService.deleteAllByHanke(hanke.id)
        logger.info { "Deleted all attachments from hanke ${hanke.logString()}" }
    }

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }
}
