package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.configuration.LockService
import java.time.OffsetDateTime
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!test")
class AlluUpdateService(
    private val alluClient: AlluClient,
    private val historyService: HakemusHistoryService,
    private val lockService: LockService,
) {
    @Scheduled(
        fixedDelayString = "\${haitaton.allu.updateIntervalMilliSeconds}",
        initialDelayString = "\${haitaton.allu.updateInitialDelayMilliSeconds}")
    fun checkApplicationStatuses() {
        logger.info(
            "Trying to obtain lock $LOCK_NAME to start checking Allu application histories.")
        lockService.doIfUnlocked(LOCK_NAME) { getApplicationStatuses() }
    }

    private fun getApplicationStatuses() {
        val ids = historyService.getAllAlluIds()
        if (ids.isEmpty()) {
            // Exit if there are no alluids. Allu handles an empty list as "all", which we don't
            // want.
            return
        }
        val lastUpdate = historyService.getLastUpdateTime()
        val currentUpdate = OffsetDateTime.now()

        logger.info {
            "Updating application histories with date $lastUpdate and ${ids.size} Allu IDs"
        }
        val applicationHistories =
            alluClient.getApplicationStatusHistories(ids, lastUpdate.toZonedDateTime())

        historyService.handleHakemusUpdates(applicationHistories, currentUpdate)
    }

    companion object {
        internal const val LOCK_NAME = "alluHistoryUpdate"
    }
}
