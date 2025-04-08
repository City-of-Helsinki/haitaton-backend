package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.domain.HankeReminder
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
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
            logger.info { "Got ${ids.size} hanke to try to complete." }

            doForEachId(ids) { id -> completionService.completeHankeIfPossible(id) }
        }
    }

    @Scheduled(cron = "\${haitaton.hanke.completions.reminderCron}", zone = "Europe/Helsinki")
    fun sendCompletionReminders() {
        if (featureFlags.isDisabled(Feature.HANKE_COMPLETION)) {
            logger.info { "Hanke completion is disabled, not checking for reminders to send." }
            return
        }

        logger.info {
            "Trying to obtain lock $LOCK_NAME to start checking for hanke completion reminders"
        }

        lockService.withLock(LOCK_NAME, 10, TimeUnit.MINUTES) {
            sendReminders(HankeReminder.COMPLETION_14)
            sendReminders(HankeReminder.COMPLETION_5)
        }
    }

    private fun sendReminders(reminder: HankeReminder) {
        val ids = completionService.idsForReminders(reminder)

        logger.info { "Got ${ids.size} hanke for sending reminder $reminder." }

        doForEachId(ids) { id -> completionService.sendReminderIfNecessary(id, reminder) }
    }

    private fun doForEachId(ids: List<Int>, f: (Int) -> Unit) {
        ids.forEach { id ->
            try {
                f(id)
            } catch (e: HankeValidityException) {
                // Don't fail on hanke validation issues, log the error and continue.
                logger.error(e) { e.message }
            }
        }
    }

    companion object {
        internal const val LOCK_NAME = "hankeCompletion"
    }
}
