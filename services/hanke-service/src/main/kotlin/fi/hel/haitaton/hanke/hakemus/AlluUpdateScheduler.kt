package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.configuration.LockService
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
@Profile("!test")
class AlluUpdateScheduler(
    private val alluUpdateService: AlluUpdateService,
    private val lockService: LockService,
) {
    @Scheduled(
        fixedDelayString = "\${haitaton.allu.updateIntervalMilliSeconds}",
        initialDelayString = "\${haitaton.allu.updateInitialDelayMilliSeconds}",
    )
    fun checkApplicationHistories() {
        logger.info(
            "Trying to obtain lock $LOCK_NAME to start checking Allu application histories."
        )
        lockService.doIfUnlocked(LOCK_NAME) { alluUpdateService.handleUpdates() }
    }

    companion object {
        internal const val LOCK_NAME = "alluHistoryUpdate"
    }
}
