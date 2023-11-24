package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MigrationResult
import fi.hel.haitaton.hanke.attachment.common.UnMigratedHankeAttachment
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
    fun unMigratedAttachment(): UnMigratedHankeAttachment? {
        logAttachmentAmountToMigrate()
        return pickOneForProcessing()?.let { (file, meta) ->
            UnMigratedHankeAttachment(
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

    /** Moves content to cloud, returns the migrated attachment id and the created blob path. */
    fun migrate(attachment: UnMigratedHankeAttachment): MigrationResult =
        attachment.let { (id, hankeId, content) ->
            val blobPath = HankeAttachmentContentService.generateBlobPath(hankeId)
            fileClient.upload(
                container = HANKE_LIITTEET,
                path = blobPath,
                originalFilename = content.fileName,
                contentType = AttachmentValidator.parseMediaType(content.contentType),
                content = content.bytes,
            )
            return MigrationResult(attachmentId = id, blobPath = blobPath)
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
