package fi.hel.haitaton.hanke.attachment.taydennys

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.hasInfected
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
    private val attachmentContentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) {
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
        scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(taydennys)

        val newAttachment =
            saveAttachment(taydennys, attachment.bytes, filename, contentType, attachmentType)

        return newAttachment.toDto()
    }

    private fun findTaydennys(taydennysId: UUID): TaydennysIdentifier =
        taydennysRepository.findByIdOrNull(taydennysId)
            ?: throw TaydennysNotFoundException(taydennysId)

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }

    private fun saveAttachment(
        taydennys: TaydennysIdentifier,
        content: ByteArray,
        filename: String,
        contentType: MediaType,
        attachmentType: ApplicationAttachmentType,
    ): TaydennysAttachmentMetadata {
        logger.info { "Saving attachment content for täydennys. ${taydennys.logString()}" }
        val blobPath =
            attachmentContentService.upload(filename, contentType, content, taydennys.hakemusId())
        logger.info { "Saving attachment metadata for täydennys. ${taydennys.logString()}" }
        val newAttachment =
            try {
                metadataService.create(
                    filename,
                    contentType.toString(),
                    content.size.toLong(),
                    blobPath,
                    attachmentType,
                    taydennys.id,
                )
            } catch (e: Exception) {
                logger.error(e) {
                    "Attachment metadata save failed, deleting attachment content $blobPath"
                }
                attachmentContentService.delete(blobPath)
                throw e
            }
        logger.info {
            "Added attachment metadata ${newAttachment.id} and content $blobPath for täydennys. ${taydennys.logString()}"
        }
        return newAttachment
    }
}
