package fi.hel.haitaton.hanke.attachment.taydennys

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
import fi.hel.haitaton.hanke.taydennys.TaydennysIdentifier
import fi.hel.haitaton.hanke.taydennys.TaydennysNotFoundException
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class TaydennysAttachmentService(
    private val metadataService: TaydennysAttachmentMetadataService,
    private val taydennysRepository: TaydennysRepository,
    private val contentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) : AttachmentService<TaydennysIdentifier, TaydennysAttachmentMetadata> {
    fun getMetadataList(taydennysId: UUID): List<TaydennysAttachmentMetadata> =
        metadataService.getMetadataList(taydennysId)

    fun addAttachment(
        taydennysId: UUID,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile,
    ): TaydennysAttachmentMetadataDto {
        logger.info {
            "Adding attachment to täydennys, taydennysId = $taydennysId, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }
        AttachmentValidator.validateSize(attachment.bytes.size)
        val filename = AttachmentValidator.validFilename(attachment.originalFilename)
        AttachmentValidator.validateExtensionForType(filename, attachmentType)
        val taydennys = findTaydennys(taydennysId)

        val contentType = AttachmentValidator.ensureMediaType(attachment.contentType)
        scanClient.scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(taydennys)

        val newAttachment =
            saveAttachment(taydennys, attachment.bytes, filename, contentType, attachmentType)

        return newAttachment.toDto()
    }

    private fun findTaydennys(taydennysId: UUID): TaydennysIdentifier =
        taydennysRepository.findByIdOrNull(taydennysId)
            ?: throw TaydennysNotFoundException(taydennysId)

    fun deleteAttachment(attachmentId: UUID) {
        val attachment = metadataService.findAttachment(attachmentId)
        logger.info { "Deleting attachment metadata ${attachment.id}" }
        metadataService.deleteAttachmentById(attachment.id)
        logger.info { "Deleting attachment content at ${attachment.blobLocation}" }
        contentService.delete(attachment.blobLocation)
        logger.info { "Deleted attachment $attachmentId from täydennys ${attachment.taydennysId}" }
    }

    fun deleteAllAttachments(taydennys: TaydennysIdentifier) {
        logger.info { "Deleting all attachments from täydennys. ${taydennys.logString()}" }
        val paths = metadataService.deleteAllAttachments(taydennys)
        try {
            paths.forEach(contentService::delete)
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to delete all attachment content for täydennys. Continuing with täydennys deletion regardless of error. ${taydennys.logString()}"
            }
        }
        logger.info { "Deleted all attachments from täydennys. ${taydennys.logString()}" }
    }

    override fun findMetadata(attachmentId: UUID): TaydennysAttachmentMetadata =
        metadataService.findAttachment(attachmentId)

    override fun findContent(attachment: TaydennysAttachmentMetadata): ByteArray =
        contentService.find(attachment.blobLocation, attachment.id)

    override fun upload(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        entity: TaydennysIdentifier,
    ): String = contentService.upload(filename, contentType, content, entity.hakemusId())

    override fun createMetadata(
        filename: String,
        contentType: String,
        size: Long,
        blobPath: String,
        entity: TaydennysIdentifier,
        attachmentType: ApplicationAttachmentType?,
    ): TaydennysAttachmentMetadata =
        metadataService.create(filename, contentType, size, blobPath, attachmentType!!, entity.id)

    override fun delete(blobPath: String): Boolean = contentService.delete(blobPath)
}
