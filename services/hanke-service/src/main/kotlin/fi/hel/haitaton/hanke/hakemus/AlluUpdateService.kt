package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluEventError
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.minusMillis
import java.time.OffsetDateTime
import kotlin.collections.forEach
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class AlluUpdateService(
    private val alluClient: AlluClient,
    private val historyService: HakemusHistoryService,
) {
    /** Handles updates from Allu - first new updates, then previously failed updates. */
    @Transactional
    fun handleUpdates() {
        val errors = historyService.getAllErrors()
        logger.info {
            "There are ${errors.size} past errors before this history update. Allu IDs: ${errors.map { it.alluId }}"
        }
        logger.info { "Handling new Allu updates..." }
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
    private fun handleNewUpdates(errors: List<AlluEventError>) {
        val ids = historyService.getAllAlluIds()
        if (ids.isNotEmpty()) {
            val lastUpdate = historyService.getLastUpdateTime()
            val currentTime = OffsetDateTime.now()

            logger.info {
                "Preparing to update ${ids.size} applications from Allu since $lastUpdate"
            }

            val applicationHistories =
                alluClient.getApplicationStatusHistories(ids, lastUpdate.toZonedDateTime())
            logger.info {
                "Received ${applicationHistories.size} application histories from Allu since $lastUpdate"
            }

            if (applicationHistories.isNotEmpty()) {
                val (succeed, failed, skipped) = handleHakemusUpdates(applicationHistories, errors)
                logger.info {
                    "Handled ${applicationHistories.size} application history updates from $lastUpdate, succeeded: $succeed, failed: $failed, skipped: $skipped"
                }
            } else {
                logger.info("No application histories found in Allu for the Haitaton applications.")
            }

            historyService.setLastUpdateTime(currentTime)
            logger.info {
                "Updated last update time to $currentTime for Allu application histories."
            }
        } else {
            // Allu handles an empty list as "all", which we don't want.
            logger.info("There are no applications to update, skipping Allu history update.")
        }
    }

    private fun handleHakemusUpdates(
        applicationHistories: List<ApplicationHistory>,
        pastErrors: List<AlluEventError>,
        skipPastFailures: Boolean = true,
    ): Triple<Int, Int, Int> {
        var success = 0
        var failure = 0
        var skipped = 0
        val saveNewErrors = skipPastFailures // If we skip past failures, we save new errors
        val newErrors = mutableListOf<AlluEventError>()
        applicationHistories.forEach { applicationHistory ->
            if (
                skipPastFailures &&
                    (pastErrors.any { it.alluId == applicationHistory.applicationId } ||
                        newErrors.any { it.alluId == applicationHistory.applicationId })
            ) {
                logger.warn {
                    "Skipping application history with ${applicationHistory.events.size} events for application ${applicationHistory.applicationId} due to its past errors"
                }
                skipped++
            } else if (
                !skipPastFailures &&
                    pastErrors.none { it.alluId == applicationHistory.applicationId }
            ) {
                logger.warn {
                    "Skipping application history with ${applicationHistory.events.size} events for application ${applicationHistory.applicationId} due to it not having past errors"
                }
                skipped++
            } else {
                logger.info {
                    "Handling application history with ${applicationHistory.events.size} events for application ${applicationHistory.applicationId}"
                }
                val result = handleHakemusUpdate(applicationHistory, pastErrors)
                if (result.isSuccess) {
                    logger.info {
                        "Successfully handled application history with ${applicationHistory.events.size} events for application ${applicationHistory.applicationId}"
                    }
                    success++
                } else {
                    logger.error {
                        "Failed to handle application history with ${applicationHistory.events.size} events for application ${applicationHistory.applicationId}"
                    }
                    failure++
                    newErrors.add(
                        AlluEventError(
                            id = 0, // ID is auto-generated by the database
                            alluId = applicationHistory.applicationId,
                            eventTime = result.event!!.eventTime,
                            newStatus = result.event.newStatus,
                            applicationIdentifier = result.event.applicationIdentifier,
                            targetStatus = result.event.targetStatus,
                            stackTrace = result.stackTrace!!,
                        )
                    )
                }
            }
        }

        if (saveNewErrors && newErrors.isNotEmpty()) {
            historyService.saveErrors(newErrors)
            logger.info {
                "Saved ${newErrors.size} new errors for Allu events that failed to process. Allu IDs: ${newErrors.map { it.alluId }}"
            }
        }

        return Triple(success, failure, skipped)
    }

    /**
     * Handles the update of a single application history by processing all events in order.
     *
     * @return In case of an error, returns a result with the event that caused the error and the
     *   error message.
     */
    private fun handleHakemusUpdate(
        applicationHistory: ApplicationHistory,
        errors: List<AlluEventError>,
    ): ApplicationEventResult {
        applicationHistory.events
            .sortedBy { it.eventTime }
            .forEach { event ->
                logger.info {
                    "Handling event of time ${event.eventTime} for application ${applicationHistory.applicationId} (${event.applicationIdentifier}) with new status ${event.newStatus}"
                }
                try {
                    historyService.handleApplicationEvent(applicationHistory.applicationId, event)
                    logger.info {
                        "Successfully handled event of time ${event.eventTime} for application ${applicationHistory.applicationId} (${event.applicationIdentifier}) with new status ${event.newStatus}"
                    }
                    val pastError =
                        errors.find {
                            it.alluId == applicationHistory.applicationId &&
                                it.eventTime == event.eventTime
                        }
                    if (pastError != null) {
                        logger.info {
                            "Removing past error of time ${pastError.eventTime} for application ${applicationHistory.applicationId} (${event.applicationIdentifier}) after successful update"
                        }
                        historyService.deleteError(pastError)
                    }
                } catch (e: Exception) {
                    logger.error(e) {
                        "Error while handling event of time ${event.eventTime} for new status ${event.newStatus} for application ${applicationHistory.applicationId} (${event.applicationIdentifier})"
                    }
                    return ApplicationEventResult.failure(event, e.stackTraceToString())
                }
            }

        return ApplicationEventResult.success(applicationHistory)
    }

    /** Handles updates for previously failed applications by fetching their histories from Allu. */
    private fun handleFailedUpdates(errors: List<AlluEventError>) {
        if (errors.isEmpty()) {
            logger.info("No past errors found, skipping Allu history update for them.")
            return
        }

        val ids = errors.map { it.alluId }.distinct()
        val updateTime = errors.minOf { it.eventTime }.minusMillis(1)

        logger.info {
            "Preparing to update ${ids.size} previously failed applications from Allu since ${updateTime}."
        }
        val applicationHistories = alluClient.getApplicationStatusHistories(ids, updateTime)
        logger.info {
            "Received ${applicationHistories.size} application histories from Allu since $updateTime"
        }

        if (applicationHistories.isNotEmpty()) {
            val (succeed, failed, skipped) =
                handleHakemusUpdates(applicationHistories, errors, false)
            logger.info {
                "Updated ${applicationHistories.size} application histories from $updateTime, succeeded: $succeed, failed: $failed, skipped: $skipped"
            }
        } else {
            logger.error(
                "No application histories found in Allu for previously failed applications: $ids"
            )
            throw IllegalStateException(
                "No application histories found in Allu for previously failed applications: $ids"
            )
        }
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
