package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluEventError
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.minusMillis
import fi.hel.haitaton.hanke.multisectDifference
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AlluUpdateService(
    private val alluClient: AlluClient,
    private val historyService: HakemusHistoryService,
) {
    /** Handles updates from Allu - first new updates, then previously failed updates. */
    fun handleUpdates() {
        val errors = historyService.getAllErrors()
        logger.info {
            "There are ${errors.size} past errors before this history update: ${errors.joinToString(" : ") { it.toLogString() }}"
        }
        handleNewUpdates(errors)
        logger.info { "New Allu updates handled." }
        logger.info { "Handling previously failed Allu updates..." }
        handleFailedUpdates(errors)
        logger.info { "Previously failed Allu updates handled." }
    }

    /**
     * Handles new updates from Allu, fetching application histories for all applications with
     * alluIds.
     */
    private fun handleNewUpdates(pastErrors: List<AlluEventError>) {
        val ids = historyService.getAllAlluIds()
        if (ids.isEmpty()) {
            // Allu handles an empty list as "all", which we don't want.
            logger.info("There are no applications to update, skipping Allu history update.")
            return
        }

        logger.info { "Handling new Allu updates..." }

        val lastUpdate = historyService.getLastUpdateTime()
        val currentTime = ZonedDateTime.now(TZ_UTC)
        val applicationHistories = fetchApplicationHistories(ids, lastUpdate)
        val updateTime: ZonedDateTime

        if (applicationHistories.isNotEmpty()) {

            val summary = processApplicationHistories(applicationHistories, pastErrors)

            logger.info {
                "Handled ${applicationHistories.size} application histories from Allu since $lastUpdate. Summary=${summary.toLogString()}"
            }

            val (succeed, _, skipped) = summary

            if (skipped.isNotEmpty()) {
                updateTime = skipped.minOf { it.events.minOf { event -> event.eventTime } }
                logger.info {
                    "Next update time is the earliest skipped application history event."
                }
            } else if (succeed.isNotEmpty()) {
                updateTime = succeed.maxOf { it.events.maxOf { event -> event.eventTime } }
                logger.info {
                    "Next update time is the latest successful application history event."
                }
            } else {
                updateTime = currentTime
                logger.info {
                    "No skipped or successful updates found, setting next update time to current time: $updateTime"
                }
            }
        } else {
            logger.info("No application histories found in Allu for the Haitaton applications.")
            updateTime = currentTime
        }

        val newUpdateTime = updateTime.minusMillis(1).toOffsetDateTime()
        historyService.setLastUpdateTime(newUpdateTime)
        logger.info { "Updated last update time to $newUpdateTime for Allu application histories." }
    }

    private fun fetchApplicationHistories(
        ids: List<Int>,
        lastUpdate: OffsetDateTime,
    ): List<ApplicationHistory> {
        logger.info { "Preparing to update ${ids.size} applications from Allu since $lastUpdate" }

        val histories = alluClient.getApplicationStatusHistories(ids, lastUpdate.toZonedDateTime())
        logger.info {
            "Received ${histories.size} application histories from Allu since $lastUpdate"
        }

        return histories
    }

    private fun processApplicationHistories(
        applicationHistories: List<ApplicationHistory>,
        pastErrors: List<AlluEventError>,
    ): UpdateSummary {
        val success = mutableListOf<ApplicationHistory>()
        val failure = mutableListOf<ApplicationHistory>()
        val skipped = mutableListOf<ApplicationHistory>()
        val newErrors = mutableListOf<AlluEventError>()
        applicationHistories.forEachIndexed { i, applicationHistory ->
            if (
                pastErrors.any { it.alluId == applicationHistory.applicationId } ||
                    newErrors.any { it.alluId == applicationHistory.applicationId }
            ) {
                logger.warn {
                    "Skipping application history ${i+1}/${applicationHistories.size} due to its past errors: ${applicationHistory.toLogString()}"
                }
                skipped.add(applicationHistory)
            } else {
                logger.info {
                    "Handling application history ${i+1}/${applicationHistories.size}: ${applicationHistory.toLogString()}"
                }
                val result = processApplicationHistory(applicationHistory)
                if (result.isSuccess) {
                    logger.info {
                        "Successfully handled application history ${i+1}/${applicationHistories.size}: ${applicationHistory.toLogString()}"
                    }
                    success.add(applicationHistory)
                } else {
                    logger.error {
                        "Failed to handle application history ${i+1}/${applicationHistories.size}: ${applicationHistory.toLogString()}"
                    }
                    failure.add(applicationHistory)
                    newErrors.add(
                        AlluEventError(applicationHistory, result.event!!, result.stackTrace)
                    )
                }
            }
        }

        if (newErrors.isNotEmpty()) {
            historyService.saveErrors(newErrors)
            logger.info {
                "Saved ${newErrors.size} new errors for Allu events that failed to process: ${newErrors.joinToString(" : ") { it.toLogString() }}"
            }
        }

        return UpdateSummary(success, failure, skipped)
    }

    /**
     * Handles the update of a single application history by processing all events in order.
     *
     * @return In case of an error, returns a result with the event that caused the error and the
     *   error message.
     */
    private fun processApplicationHistory(
        applicationHistory: ApplicationHistory
    ): ApplicationEventResult {
        val events =
            applicationHistory.events
                .distinctBy { "${it.eventTime} - ${it.newStatus}" }
                .sortedBy { it.eventTime }
        val duplicateEvents = applicationHistory.events.multisectDifference(events)
        if (duplicateEvents.isNotEmpty()) {
            logger.warn {
                "Found ${duplicateEvents.size} duplicate events from Allu for application ${applicationHistory.applicationId}. These will be ignored: ${duplicateEvents.joinToString(" : ") { it.toLogString() }}"
            }
        }
        logger.info {
            "Processing ${events.size} events for application ${applicationHistory.applicationId}: ${events.joinToString(" : ") { it.toLogString() }}"
        }
        events.forEachIndexed { i, event ->
            logger.info {
                "Handling event ${i+1}/${events.size} for application ${applicationHistory.applicationId}: ${event.toLogString()}"
            }
            try {
                historyService.handleApplicationEvent(applicationHistory.applicationId, event)
                logger.info {
                    "Successfully handled event ${i+1}/${events.size} for application ${applicationHistory.applicationId}: ${event.toLogString()}"
                }
            } catch (e: Exception) {
                logger.error(e) {
                    "Error while handling event ${i+1}/${events.size} for application ${applicationHistory.applicationId}: ${event.toLogString()}"
                }
                return ApplicationEventResult.failure(event, e.stackTraceToString())
            }
        }

        return ApplicationEventResult.success(applicationHistory)
    }

    /** Handles updates for previously failed applications. */
    private fun handleFailedUpdates(errors: List<AlluEventError>) {
        if (errors.isEmpty()) {
            logger.info("No past errors found, skipping Allu history update for them.")
            return
        } else {
            val summary = processErrors(errors)
            logger.info {
                "Handled ${errors.size} previously failed application history updates. Summary: ${summary.toLogString()}"
            }
        }
    }

    /**
     * Processes previously failed Allu events.
     *
     * @return A summary of the update process, including successful and failed updates.
     */
    private fun processErrors(errors: List<AlluEventError>): UpdateSummary {
        val success = mutableListOf<ApplicationHistory>()
        val failure = mutableListOf<ApplicationHistory>()
        errors.forEachIndexed { i, error ->
            logger.info {
                "Handling previously failed event ${i+1}/${errors.size}: ${error.toLogString()}"
            }
            val result = processError(error)
            if (result.isSuccess) {
                historyService.deleteError(error)
                logger.info {
                    "Successfully handled previously failed event ${i+1}/${errors.size}: ${error.toLogString()}"
                }
                success.add(error.toApplicationHistory())
            } else {
                logger.error {
                    "Failed again to handle previously failed event ${i+1}/${errors.size}: ${error.toLogString()}"
                }
                failure.add(error.toApplicationHistory())
            }
        }
        return UpdateSummary(success, failure, emptyList())
    }

    /**
     * Handles a single previosly failed application history event.
     *
     * @return In case of an error, returns a result with the event that caused the error and the
     *   error message.
     */
    private fun processError(error: AlluEventError): ApplicationEventResult {
        val event = error.toApplicationStatusEvent()
        try {
            historyService.handleApplicationEvent(error.alluId, event)
        } catch (e: Exception) {
            logger.error(e) { "Error while handling earlier error: ${error.toLogString()}" }
            return ApplicationEventResult.failure(event, e.stackTraceToString())
        }

        return ApplicationEventResult.success(error.toApplicationHistory())
    }

    data class UpdateSummary(
        val success: List<ApplicationHistory>,
        val failure: List<ApplicationHistory>,
        val skipped: List<ApplicationHistory>,
    ) {
        fun toLogString(): String =
            "success=${success.size}, failure=${failure.size}, skipped=${skipped.size}"
    }

    data class ApplicationEventResult(
        val applicationHistory: ApplicationHistory? = null,
        val event: ApplicationStatusEvent? = null,
        val stackTrace: String? = null,
    ) {
        val isSuccess: Boolean
            get() = applicationHistory != null

        companion object {
            fun success(history: ApplicationHistory) = ApplicationEventResult(history)

            fun failure(event: ApplicationStatusEvent, stackTrace: String) =
                ApplicationEventResult(null, event, stackTrace)
        }
    }
}
