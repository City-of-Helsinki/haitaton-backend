package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.validNameAndType
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentService(
    private val metadataService: HankeAttachmentMetadataService,
    private val contentService: HankeAttachmentContentService,
    private val scanClient: FileScanClient,
) : AttachmentService<HankeIdentifier, HankeAttachmentMetadata> {

    fun getMetadataList(hankeTunnus: String) = metadataService.getMetadataList(hankeTunnus)

    fun uploadHankeAttachment(
        hankeTunnus: String,
        attachment: MultipartFile,
    ): HankeAttachmentMetadataDto {
        val hanke = metadataService.hankeWithRoomForAttachment(hankeTunnus)

        AttachmentValidator.validateSize(attachment.bytes.size)
        val (filename, mediatype) = attachment.validNameAndType()

        scanClient.scanAttachment(filename, attachment.bytes)

        return saveAttachment(hanke, attachment.bytes, filename, mediatype).toDto()
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

    override fun findMetadata(attachmentId: UUID): HankeAttachmentMetadata =
        metadataService.findAttachment(attachmentId)

    override fun findContent(attachment: HankeAttachmentMetadata): ByteArray =
        contentService.find(attachment)

    override fun upload(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        entity: HankeIdentifier,
    ): String =
        contentService.upload(
            fileName = filename,
            contentType = contentType,
            content = content,
            hankeId = entity.id,
        )

    override fun createMetadata(
        filename: String,
        contentType: String,
        size: Long,
        blobPath: String,
        entity: HankeIdentifier,
        attachmentType: ApplicationAttachmentType?,
    ): HankeAttachmentMetadata =
        metadataService.saveAttachment(entity.hankeTunnus, filename, contentType, size, blobPath)

    override fun delete(blobPath: String) = contentService.delete(blobPath)
}
