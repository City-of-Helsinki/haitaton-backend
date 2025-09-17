package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import java.time.ZonedDateTime
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AlluUpdateService(
    private val alluClient: AlluClient,
    private val historyService: HakemusHistoryService,
) {
    /** Handles updates from Allu. */
    fun handleUpdates() {
        val ids = historyService.getAllAlluIds()
        if (ids.isEmpty()) {
            // Allu handles an empty list as "all", which we don't want.
            logger.info("There are no applications to update, skipping Allu history update.")
            return
        }
        val lastUpdate = historyService.getLastUpdateTime()
        val currentTime = ZonedDateTime.now(TZ_UTC)
        val applicationHistories = fetchApplicationHistories(ids, lastUpdate)
        historyService.setLastUpdateTime(currentTime)
        historyService.processApplicationHistories(applicationHistories)
    }

    private fun fetchApplicationHistories(
        ids: List<Int>,
        lastUpdate: ZonedDateTime,
    ): List<ApplicationHistory> {
        logger.info {
            "Preparing to fetch application histories for ${ids.size} applications from Allu since $lastUpdate"
        }
        val histories = alluClient.getApplicationStatusHistories(ids, lastUpdate)
        logger.info {
            "Received ${histories.size} application histories from Allu since $lastUpdate"
        }
        return histories
    }
}
