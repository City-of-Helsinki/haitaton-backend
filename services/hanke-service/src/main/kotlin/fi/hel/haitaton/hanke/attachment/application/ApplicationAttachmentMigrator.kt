package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileClient
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentMigrator(
    private val fileClient: FileClient,
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val contentRepository: ApplicationAttachmentContentRepository,
) {
    /** Find an attachment that is not yet migrated, i.e. still has content in the db. */
    @Transactional(readOnly = true)
    fun findAttachmentWithDatabaseContent(): ApplicationAttachmentWithContent? {
        return attachmentAmountToMigrate().let {
            if (it == 0L) {
                return null
            }
            logger.info {
                logger.info { "There are un-migrated application attachments left, count = $it" }
            }
            pickForMigration().let { (attachmentEntity, contentEntity) ->
                ApplicationAttachmentWithContent(
                    id = attachmentEntity.id!!,
                    applicationId = attachmentEntity.applicationId,
                    content =
                        AttachmentContent(
                            fileName = attachmentEntity.fileName,
                            contentType = attachmentEntity.contentType,
                            bytes = contentEntity.content,
                        ),
                )
            }
        }
    }

    /** Moves content to cloud, returns the created blob path. */
    fun migrate(attachment: ApplicationAttachmentWithContent): String {
        val blobPath = generateBlobPath(attachment.applicationId)
        fileClient.upload(
            container = Container.HAKEMUS_LIITTEET,
            path = blobPath,
            originalFilename = attachment.content.fileName,
            contentType = AttachmentValidator.parseMediaType(attachment.content.contentType),
            content = attachment.content.bytes,
        )
        return blobPath
    }

    /**
     * Set new blob location for an attachment and remove the content from old location in the db.
     */
    @Transactional
    fun setBlobPathAndCleanup(attachmentId: UUID, blobPath: String) {
        val attachment =
            attachmentRepository.findByIdOrNull(attachmentId)
                ?: throw AttachmentNotFoundException(attachmentId)
        attachment.blobLocation = blobPath
        attachmentRepository.save(attachment)
        contentRepository.deleteById(attachmentId)
    }

    /** Revert migration by deleting the blob content. */
    fun revertMigration(blobPath: String) {
        fileClient.delete(Container.HAKEMUS_LIITTEET, blobPath)
    }

    private fun attachmentAmountToMigrate() =
        contentRepository.count().also {
            logger.info { "There are un-migrated application attachments left, count = $it" }
        }

    /**
     * An attachment with its content. Content references attachment data. Thus, the relation must
     * exist.
     */
    private fun pickForMigration():
        Pair<ApplicationAttachmentEntity, ApplicationAttachmentContentEntity> {
        val attachmentEntity =
            attachmentRepository.findFirstByBlobLocationIsNull()
                ?: error("No attachments to migrate")
        val contentEntity =
            contentRepository.findByIdOrNull(attachmentEntity.id!!)
                ?: throw AttachmentContentNotFoundException(attachmentEntity.id!!)
        return Pair(attachmentEntity, contentEntity)
    }
}

data class ApplicationAttachmentWithContent(
    val id: UUID,
    val applicationId: Long,
    val content: AttachmentContent
)
