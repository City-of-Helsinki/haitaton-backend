package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.common.UnMigratedHankeAttachment
import fi.hel.haitaton.hanke.configuration.LockService
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@ConditionalOnProperty(
    name = ["haitaton.attachment-migration.hanke.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class HankeAttachmentMigrationScheduler(
    private val lockService: LockService,
    private val hankeAttachmentMigrator: HankeAttachmentMigrator,
) {
    companion object {
        /** Name for this scheduled task in [LockService]. */
        const val MIGRATE_HANKE_ATTACHMENT = "Migrate hanke attachment"
    }

    @Scheduled(
        initialDelayString = "\${haitaton.attachment-migration.hanke.initial-delay}",
        fixedDelayString = "\${haitaton.attachment-migration.hanke.interval}"
    )
    fun scheduleMigrate() =
        lockService.doIfUnlocked(MIGRATE_HANKE_ATTACHMENT) {
            hankeAttachmentMigrator.unMigratedAttachment()?.let { migrate(it) }
                ?: logger.info { "No hanke attachments to migrate" }
        }

    private fun migrate(attachment: UnMigratedHankeAttachment) =
        hankeAttachmentMigrator.migrate(attachment).also { (id, path) ->
            hankeAttachmentMigrator.setBlobPathAndCleanup(attachmentId = id, blobPath = path)
            logger.info { "Hanke attachment $id migrated to $path successfully" }
        }
}
