package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.AlluEventEntity
import fi.hel.haitaton.hanke.allu.AlluEventRepository
import fi.hel.haitaton.hanke.allu.AlluEventStatus
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.findPendingAndFailedEventsGrouped
import java.time.Instant
import java.time.OffsetDateTime
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HakemusHistoryService(
    private val hakemusRepository: HakemusRepository,
    private val alluStatusRepository: AlluStatusRepository,
    private val alluEventRepository: AlluEventRepository,
    private val applicationEventService: ApplicationEventService,
) {
    @Transactional(readOnly = true) fun getAllAlluIds() = hakemusRepository.getAllAlluIds()

    @Transactional(readOnly = true)
    fun getLastUpdateTime() = alluStatusRepository.getLastUpdateTime()

    @Transactional
    fun setLastUpdateTime(time: OffsetDateTime) {
        val status = alluStatusRepository.getReferenceById(1)
        status.historyLastUpdated = time
    }

    @Transactional
    fun processApplicationHistories(applicationHistories: List<ApplicationHistory>) {
        if (applicationHistories.isNotEmpty()) {
            logger.info {
                "Processing ${applicationHistories.size} new application histories from Allu: " +
                    applicationHistories.joinToString(" : ") { it.toLogString() }
            }
            saveEventsAsPending(applicationHistories)
        }
        processPendingAndFailedEvents()
    }

    private fun saveEventsAsPending(applicationHistories: List<ApplicationHistory>) {
        val events = mutableListOf<AlluEventEntity>()
        applicationHistories.forEach { history ->
            history.events.forEach { event ->
                events.add(
                    AlluEventEntity(
                        id = 0,
                        alluId = history.applicationId,
                        eventTime = event.eventTime.toOffsetDateTime(),
                        newStatus = event.newStatus,
                        applicationIdentifier = event.applicationIdentifier,
                        targetStatus = event.targetStatus,
                    )
                )
            }
        }
        alluEventRepository.batchInsertIgnoreDuplicates(events)
        logger.info { "Saved ${events.size} new Allu history events as PENDING." }
    }

    private fun processPendingAndFailedEvents() {
        val applicationEvents = alluEventRepository.findPendingAndFailedEventsGrouped()

        if (applicationEvents.isEmpty()) {
            logger.info("No pending or failed Allu events to process.")
            return
        }

        logger.info { "Processing ${applicationEvents.size} pending or failed Allu events." }

        applicationEvents.forEach { alluId, events ->
            logger.info {
                "Processing events for Allu ID $alluId: ${events.joinToString(" : ") { it.toLogString() }}"
            }
            processApplicationEvents(alluId, events)
        }
    }

    private fun processApplicationEvents(alluId: Int, events: List<AlluEventEntity>) {
        // Find the earliest failed event
        val earliestFailedEvent = events.firstOrNull { it.status == AlluEventStatus.FAILED }

        if (earliestFailedEvent != null) {
            // Only retry the earliest failed event
            try {
                logger.info {
                    "Retrying earliest failed Allu event for Allu ID $alluId: ${earliestFailedEvent.toLogString()}"
                }
                earliestFailedEvent.retryCount++
                applicationEventService.handleApplicationEvent(
                    alluId,
                    earliestFailedEvent.toApplicationStatusEvent(),
                )
                logger.info {
                    "Successfully retried Allu event for Allu ID $alluId: ${earliestFailedEvent.toLogString()}"
                }
                earliestFailedEvent.status = AlluEventStatus.PROCESSED
                earliestFailedEvent.stackTrace = null // Clear stack trace on success
                earliestFailedEvent.processedAt = Instant.now()
                // Recursively process remaining events
                processApplicationEvents(alluId, events)
            } catch (e: Exception) {
                logger.error(e) {
                    "Failed to retry Allu event for Allu ID $alluId: ${earliestFailedEvent.toLogString()}"
                }
                earliestFailedEvent.stackTrace = e.stackTraceToString()
                // If retry fails, all pending events remain PENDING
                return
            }
        }

        val pendingEvents = events.filter { it.status == AlluEventStatus.PENDING }
        for (event in pendingEvents) {
            try {
                logger.info {
                    "Processing pending Allu event for Allu ID $alluId: ${event.toLogString()}"
                }
                applicationEventService.handleApplicationEvent(
                    alluId,
                    event.toApplicationStatusEvent(),
                )
                logger.info {
                    "Successfully processed Allu event for Allu ID $alluId: ${event.toLogString()}"
                }
                event.status = AlluEventStatus.PROCESSED
                event.processedAt = Instant.now()
            } catch (e: Exception) {
                logger.error(e) {
                    "Failed to process Allu event for Allu ID $alluId: ${event.toLogString()}"
                }
                event.status = AlluEventStatus.FAILED
                event.stackTrace = e.stackTraceToString()
                break // Stop processing, remaining events stay PENDING
            }
        }
    }

    @Transactional
    fun deleteOldProcessedEvents(days: Int) {
        alluEventRepository.deleteProcessedEventsOlderThan(
            OffsetDateTime.now(TZ_UTC).minusSeconds(days * 24 * 60 * 60L)
        )
        logger.info { "Deleted Allu events older than $days days that were marked as PROCESSED." }
    }
}
