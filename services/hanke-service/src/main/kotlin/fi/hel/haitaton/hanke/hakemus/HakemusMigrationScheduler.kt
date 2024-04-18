package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.configuration.LockService
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@ConditionalOnProperty("haitaton.migration.enabled")
class HakemusMigrationScheduler(
    private val hakemusMigrationService: HakemusMigrationService,
    private val hankeRepository: HankeRepository,
    private val lockService: LockService,
) {
    private val lockName = "kortistoMigration"

    @EventListener(ApplicationReadyEvent::class)
    fun startMigration() {
        lockService.doIfUnlocked(lockName, this::migrate)
    }

    private fun migrate() {
        logger.info("Starting migration to Kortisto")
        val hankeIds = hankeRepository.getAllIds()
        logger.info("Found ${hankeIds.size} hanke to migrate")
        for ((i, hankeId) in hankeIds.withIndex()) {
            logger.info { "Migrating hanke ${i+1}/${hankeIds.size}, id=$hankeId" }
            hakemusMigrationService.migrateOneHanke(hankeId)
            logger.info { "Done migrating hanke ${i+1}/${hankeIds.size}, id=$hankeId" }
        }
        logger.info("Finished migration to Kortisto")
    }
}
