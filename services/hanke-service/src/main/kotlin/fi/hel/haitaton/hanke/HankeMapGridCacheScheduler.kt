package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.LockService
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!test")
class HankeMapGridCacheScheduler(
    private val hankeMapGridService: HankeMapGridService,
    private val lockService: LockService,
) {
    /**
     * Repopulates the cache for public hanke grid data every 5 minutes. This ensures the cached
     * data stays fresh and includes newly published hanke.
     */
    @Scheduled(
        fixedDelayString = "\${haitaton.map-grid.cache-repopulation-interval-ms}",
        initialDelayString = "\${haitaton.map-grid.cache-initial-delay-ms}",
    )
    fun repopulateCache() {
        logger.info { "Trying to obtain lock $LOCK_NAME to start cache repopulation." }

        var wasExecuted = false
        lockService.doIfUnlocked(LOCK_NAME) {
            try {
                hankeMapGridService.repopulateCache()
                logger.info { "Cache repopulation completed successfully." }
                wasExecuted = true
            } catch (e: Exception) {
                logger.error(e) { "Cache repopulation failed: ${e.message}" }
            }
        }
        if (!wasExecuted) {
            logger.info { "Lock $LOCK_NAME is already held, skipping cache repopulation." }
        }
    }

    companion object {
        internal const val LOCK_NAME = "hankeMapGridCacheRepopulation"
    }
}
