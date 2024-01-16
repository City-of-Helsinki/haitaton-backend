package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentContentTransferService(
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val attachmentContentRepository: ApplicationAttachmentContentRepository,
    private val fileClient: FileClient,
) {
    fun nextTransferableAttachment(): ApplicationAttachmentEntity? =
        attachmentRepository.findByBlobLocationIsNull().firstOrNull()

    fun transferToBlob(attachmentEntity: ApplicationAttachmentEntity): String {
        logger.info { "Transferring content to blob storage for attachment ${attachmentEntity.id}" }
        val content =
            attachmentContentRepository.findByIdOrNull(attachmentEntity.id)
                ?: throw AttachmentContentNotFoundException(attachmentEntity.id)
        val blobPath = generateBlobPath(attachmentEntity.applicationId)
        logger.info { "Uploading attachment ${attachmentEntity.id} content to $blobPath" }
        fileClient.upload(
            Container.HAKEMUS_LIITTEET,
            blobPath,
            attachmentEntity.fileName,
            MediaType.parseMediaType(attachmentEntity.contentType),
            content.content,
        )
        logger.info { "Attachment ${attachmentEntity.id} content uploaded to $blobPath" }
        return blobPath
    }

    @Transactional
    fun cleanUpDatabase(attachmentEntity: ApplicationAttachmentEntity, blobPath: String) {
        logger.info { "Cleaning up database for attachment ${attachmentEntity.id}" }
        attachmentEntity.blobLocation = blobPath
        logger.info { "Updating attachment ${attachmentEntity.id} with blob location $blobPath" }
        attachmentRepository.save(attachmentEntity)
        logger.info { "Deleting attachment ${attachmentEntity.id} content from database" }
        attachmentContentRepository.deleteById(attachmentEntity.id!!)
        logger.info { "Attachment ${attachmentEntity.id} content deleted from database" }
    }
}
