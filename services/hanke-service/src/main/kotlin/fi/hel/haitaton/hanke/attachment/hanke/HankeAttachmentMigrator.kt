package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.UnmigratedHankeAttachment
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Component
class HankeAttachmentMigrator(
    private val fileClient: FileClient,
    private val attachmentRepository: HankeAttachmentRepository,
    private val contentRepository: HankeAttachmentContentRepository
) {
    /** Find an attachment that is not yet migrated, i.e. still has content in the db. */
    @Transactional(readOnly = true)
    fun findAttachmentWithDatabaseContent(): UnmigratedHankeAttachment? {
        logAttachmentAmountToMigrate()
        return pickOneForProcessing()?.let { (file, meta) ->
            UnmigratedHankeAttachment(
                attachmentId = file.attachmentId,
                hankeId = meta.hanke.id,
                content =
                    AttachmentContent(
                        fileName = meta.fileName,
                        contentType = meta.contentType,
                        bytes = file.content
                    )
            )
        }
    }

    /** Moves content to cloud, returns the created blob path. */
    fun migrate(attachment: UnmigratedHankeAttachment): String {
        val blobPath = HankeAttachmentContentService.generateBlobPath(attachment.hankeId)
        fileClient.upload(
            container = HANKE_LIITTEET,
            path = blobPath,
            originalFilename = attachment.content.fileName,
            contentType = AttachmentValidator.parseMediaType(attachment.content.contentType),
            content = attachment.content.bytes,
        )
        return blobPath
    }

    /**
     * Set new blob location for an attachment and remove the content from old location in the db.
     * Tries to delete the blob if the db operations fail.
     */
    @Transactional
    fun setBlobPathAndCleanup(attachmentId: UUID, blobPath: String) {
        try {
            val metadata =
                attachmentRepository.findByIdOrNull(attachmentId)
                    ?: error("Attachment $attachmentId missing")
            metadata.blobLocation = blobPath
            attachmentRepository.save(metadata)
            contentRepository.deleteByAttachmentId(attachmentId)
        } catch (e: Exception) {
            logger.error {
                "Attachment migration failed on db update. Deleting blob $blobPath for data consistency."
            }
            fileClient.delete(HANKE_LIITTEET, blobPath)
        }
    }

    /**
     * A file with its metadata. Content references attachment data. Thus, the relation must exist.
     */
    private fun pickOneForProcessing(): Pair<HankeAttachmentContentEntity, HankeAttachmentEntity>? {
        val content = contentRepository.pickOne() ?: return null
        val metadata = attachmentRepository.findByIdOrNull(content.attachmentId)!!
        return Pair(content, metadata)
    }

    private fun logAttachmentAmountToMigrate() =
        contentRepository.count().also {
            logger.info { "There are $it un-migrated hanke attachments left." }
        }
}
