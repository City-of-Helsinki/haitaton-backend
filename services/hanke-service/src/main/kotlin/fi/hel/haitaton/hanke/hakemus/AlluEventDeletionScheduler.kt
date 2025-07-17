package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.configuration.LockService
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!test")
class AlluEventDeletionScheduler(
    private val hakemusHistoryService: HakemusHistoryService,
    private val lockService: LockService,
    @Value("\${haitaton.allu.eventDeleteExpirationDays}") private val eventDeleteExpirationDays: Int,
) {
    @Scheduled(cron = "\${haitaton.allu.eventDeleteCron}", zone = "Europe/Helsinki")
    fun deleteOldEvents() {
        logger.info(
            "Trying to obtain lock $LOCK_NAME to start delete older than $eventDeleteExpirationDays days processed Allu events."
        )
        lockService.doIfUnlocked(LOCK_NAME) {
            hakemusHistoryService.deleteOldProcessedEvents(eventDeleteExpirationDays)
        }
    }

    companion object {
        internal const val LOCK_NAME = "alluEventDelete"
    }
}
