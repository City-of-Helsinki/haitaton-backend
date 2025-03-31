package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.LockService
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!test")
class HankeCompletionScheduler(
    private val completionService: HankeCompletionService,
    private val lockService: LockService,
    private val featureFlags: FeatureFlags,
) {
    @Scheduled(cron = "\${haitaton.hanke.completions.cron}", zone = "Europe/Helsinki")
    fun completeHankkeet() {
        if (featureFlags.isDisabled(Feature.HANKE_COMPLETION)) {
            logger.info { "Hanke completion is disabled, not running daily completion job." }
            return
        }

        logger.info(
            "Trying to obtain lock $LOCK_NAME to start checking for hanke that need to be completed."
        )
        lockService.withLock(LOCK_NAME, 10, TimeUnit.MINUTES) {
            val ids = completionService.getPublicIds()
            logger.info("Got ${ids.size} hanke to try to complete.")

            ids.forEach { id ->
                try {
                    completionService.completeHankeIfPossible(id)
                } catch (e: HankeValidityException) {
                    // Don't fail on hanke validation issues, log the error and continue.
                    logger.error(e) { e.message }
                }
            }
        }
    }

    companion object {
        internal const val LOCK_NAME = "hankeCompletion"
    }
}
