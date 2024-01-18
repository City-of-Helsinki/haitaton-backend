package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.configuration.LockService
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@ConditionalOnProperty(
    name = ["haitaton.attachment-migration.application.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class ApplicationAttachmentMigrationScheduler(
    private val lockService: LockService,
    private val applicationAttachmentMigrator: ApplicationAttachmentMigrator,
) {
    companion object {
        /** Name for this scheduled task in [LockService]. */
        const val LOCK_NAME = "applicationAttachmentContentMigration"
    }

    @Scheduled(
        initialDelayString = "\${haitaton.attachment-migration.application.initial-delay}",
        fixedDelayString = "\${haitaton.attachment-migration.application.interval}",
    )
    fun scheduleMigrate() =
        lockService.doIfUnlocked(LOCK_NAME) {
            applicationAttachmentMigrator.findAttachmentWithDatabaseContent()?.let { migrate(it) }
                ?: logger.info { "No application attachments to migrate" }
        }

    /**
     * Migrate the given attachment by 1) saving the content into blob storage and 2) setting
     * blob_location and deleting db content.
     */
    private fun migrate(attachment: ApplicationAttachmentWithContent) {
        logger.info {
            "Starting to migrate application attachment content from database to Azure Blob Storage, id = ${attachment.id}"
        }
        val blobPath = applicationAttachmentMigrator.migrate(attachment)
        logger.info {
            "Attachment content migrated to blob, id = ${attachment.id}, blobPath = $blobPath"
        }
        try {
            applicationAttachmentMigrator.setBlobPathAndCleanup(attachment.id, blobPath)
        } catch (e: Exception) {
            logger.error(e) {
                "Error while setting blob path and cleaning up database, id = ${attachment.id}, blobPath = $blobPath"
            }
            applicationAttachmentMigrator.revertMigration(blobPath)
            logger.info {
                "Deleted migrated blob content for attachment, id = ${attachment.id}, blobPath = $blobPath"
            }
            throw e
        }
        logger.info {
            "Attachment migration database cleanup completed, id = ${attachment.id}, blobPath = $blobPath"
        }
        logger.info {
            "Application attachment migrated to blob successfully, id = ${attachment.id}, blobPath = $blobPath"
        }
    }
}
