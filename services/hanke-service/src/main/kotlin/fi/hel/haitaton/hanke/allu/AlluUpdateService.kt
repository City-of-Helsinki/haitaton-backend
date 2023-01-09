package fi.hel.haitaton.hanke.allu

import java.time.OffsetDateTime
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AlluUpdateService(
    private val applicationRepository: ApplicationRepository,
    private val alluStatusRepository: AlluStatusRepository,
    private val cableReportService: CableReportService,
    private val applicationService: ApplicationService,
) {

    @Scheduled(fixedDelay = 1000 * 60, initialDelay = 1000 * 60)
    fun checkApplicationStatuses() {
        val ids = applicationRepository.getAllAlluIds()
        if (ids.isEmpty()) {
            // Exit if there are no alluids. Allu handles an empty list as "all", which we don't
            // want.
            return
        }
        val lastUpdate = alluStatusRepository.getLastUpdateTime()
        val currentUpdate = OffsetDateTime.now()

        logger.info {
            "Updating application histories with date $lastUpdate and ${ids.size} Allu IDs"
        }
        val applicationHistories =
            cableReportService.getApplicationStatusHistories(ids, lastUpdate.toZonedDateTime())

        applicationService.handleApplicationUpdates(applicationHistories, currentUpdate)
    }
}
