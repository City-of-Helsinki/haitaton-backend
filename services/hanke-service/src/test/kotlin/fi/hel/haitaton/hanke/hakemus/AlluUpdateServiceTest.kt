package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.contains
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluEventError
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.DEFAULT_EVENT_TIME
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.asList
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withDefaultEvents
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withEvent
import fi.hel.haitaton.hanke.minusMillis
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
import io.mockk.verifySequence
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class AlluUpdateServiceTest {
    private val alluClient: AlluClient = mockk()
    private val historyService: HakemusHistoryService = mockk()

    private val alluUpdateService = AlluUpdateService(alluClient, historyService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(historyService, alluClient)
    }

    @Test
    fun `does nothing when no alluIds or errors`(output: CapturedOutput) {
        every { historyService.getAllErrors() } returns listOf()
        every { historyService.getAllAlluIds() } returns listOf()

        alluUpdateService.handleUpdates()

        assertThat(output)
            .contains("There are no applications to update, skipping Allu history update.")
        assertThat(output).contains("No past errors found, skipping Allu history update for them.")
        verifySequence {
            historyService.getAllErrors()
            historyService.getAllAlluIds()
            alluClient wasNot Called
        }
    }

    @Nested
    inner class HandleNewUpdates {
        private val lastUpdatedString = "2022-10-12T15:23:34.981654Z"
        private val lastUpdated = OffsetDateTime.parse(lastUpdatedString)
        private val eventsAfter = ZonedDateTime.parse(lastUpdatedString)

        @Test
        fun `calls Allu with alluids and last update date`() {
            val alluids = listOf(23, 24)
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `sets update time to earliest skipped event time`() {
            val alluids = listOf(23, 24)
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withDefaultEvents()
                    .asList()
            val newUpdateTime =
                histories.single().events.minOf { it.eventTime }.minusMillis(1).toOffsetDateTime()
            val error =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = newUpdateTime.toZonedDateTime(),
                )
            val errorEvent = error.toApplicationStatusEvent()
            every { historyService.getAllErrors() } returns listOf(error)
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { historyService.setLastUpdateTime(newUpdateTime) }
            justRun { historyService.handleApplicationEvent(24, errorEvent) }
            justRun { historyService.deleteError(error) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.setLastUpdateTime(newUpdateTime)
                historyService.handleApplicationEvent(24, errorEvent)
                historyService.deleteError(error)
            }
        }

        @Test
        fun `sets update time to latest successful event time when no skips`() {
            val alluids = listOf(23, 24)
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withDefaultEvents()
                    .asList()
            val events = histories.single().events
            val newUpdateTime = events.maxOf { it.eventTime }.minusMillis(1).toOffsetDateTime()
            every { historyService.getAllErrors() } returns emptyList()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { historyService.handleApplicationEvent(24, events.first()) }
            justRun { historyService.handleApplicationEvent(24, events.last()) }
            justRun { historyService.setLastUpdateTime(newUpdateTime) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleApplicationEvent(24, events.first())
                historyService.handleApplicationEvent(24, events.last())
                historyService.setLastUpdateTime(newUpdateTime)
            }
        }

        @Test
        fun `handles application events for the one with history`() {
            val alluids = listOf(23, 24)
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withDefaultEvents()
                    .asList()
            val event1 = histories.single().events.first()
            val event2 = histories.single().events.last()
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            every { historyService.getAllErrors() } returns emptyList()
            justRun { historyService.handleApplicationEvent(24, event1) }
            justRun { historyService.handleApplicationEvent(24, event2) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleApplicationEvent(24, event1)
                historyService.handleApplicationEvent(24, event2)
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `handles other application history events when one fails`() {
            val alluids = listOf(23, 24)
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 23, *emptyArray())
                        .withDefaultEvents(),
                    ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                        .withDefaultEvents(),
                )
            val event1 = histories.first().events.first()
            val event2 = histories.first().events.last()
            val exception = RuntimeException("Test exception")
            val error =
                ApplicationHistoryFactory.createError(
                    alluId = 23,
                    eventTime = event2.eventTime,
                    newStatus = event2.newStatus,
                    stackTrace = exception.stackTraceToString(),
                )
            val event3 = histories.last().events.first()
            val event4 = histories.last().events.last()
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { historyService.handleApplicationEvent(23, event1) }
            every { historyService.handleApplicationEvent(23, event2) } throws exception
            justRun { historyService.handleApplicationEvent(24, event3) }
            justRun { historyService.handleApplicationEvent(24, event4) }
            justRun { historyService.saveErrors(listOf(error)) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleApplicationEvent(23, event1)
                historyService.handleApplicationEvent(23, event2)
                historyService.handleApplicationEvent(24, event3)
                historyService.handleApplicationEvent(24, event4)
                historyService.saveErrors(listOf(error))
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `filters duplicate events`() {
            val alluids = listOf(24)
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withEvent(eventTime = DEFAULT_EVENT_TIME)
                    .withEvent(eventTime = DEFAULT_EVENT_TIME)
                    .asList()
            val event1 = histories.single().events.first()
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { historyService.handleApplicationEvent(24, event1) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleApplicationEvent(24, event1)
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `skips update for an application with errors`() {
            val eventTime = DEFAULT_EVENT_TIME
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24)
                    .withEvent(eventTime = eventTime)
                    .asList()
            val error = ApplicationHistoryFactory.createError(alluId = 24, eventTime = eventTime)
            every { historyService.getAllErrors() } returns listOf(error)
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.handleApplicationEvent(24, histories[0].events.first()) }
            justRun { historyService.deleteError(error) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.handleApplicationEvent(24, histories[0].events.first())
                historyService.deleteError(error)
            }
        }
    }

    @Nested
    inner class HandleFailedUpdates {

        @Test
        fun `handles errors and deletes them`() {
            val history =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withDefaultEvents()
            val event1 = history.events.first()
            val event2 = history.events.last()
            val error1 = AlluEventError(history, event1, "Test error 1")
            val error2 = AlluEventError(history, event2, "Test error 2")
            every { historyService.getAllErrors() } returns listOf(error1, error2)
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.handleApplicationEvent(24, event1) }
            justRun { historyService.deleteError(error1) }
            justRun { historyService.handleApplicationEvent(24, event2) }
            justRun { historyService.deleteError(error2) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.handleApplicationEvent(24, event1)
                historyService.deleteError(error1)
                historyService.handleApplicationEvent(24, event2)
                historyService.deleteError(error2)
            }
        }
    }

    @Nested
    inner class ComplexEventSequences {

        @Test
        fun `handles complex multi-status transition sequence`() {
            val alluids = listOf(24)
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(5),
                        newStatus = ApplicationStatus.PENDING,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(4),
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(3),
                        newStatus = ApplicationStatus.INFORMATION_RECEIVED,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(2),
                        newStatus = ApplicationStatus.WAITING_INFORMATION,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(1),
                        newStatus = ApplicationStatus.DECISION,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME,
                        newStatus = ApplicationStatus.OPERATIONAL_CONDITION,
                    )
                    .asList()
            val events = histories.single().events
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            events.forEach { event -> justRun { historyService.handleApplicationEvent(24, event) } }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                events.forEach { event -> historyService.handleApplicationEvent(24, event) }
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `handles interleaved events from multiple applications`() {
            val alluids = listOf(23, 24, 25)
            val baseTime = DEFAULT_EVENT_TIME
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 23, *emptyArray())
                        .withEvent(
                            eventTime = baseTime.minusMinutes(5),
                            newStatus = ApplicationStatus.PENDING,
                        )
                        .withEvent(
                            eventTime = baseTime.minusMinutes(2),
                            newStatus = ApplicationStatus.HANDLING,
                        )
                        .withEvent(
                            eventTime = baseTime.plusMinutes(1),
                            newStatus = ApplicationStatus.DECISION,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                        .withEvent(
                            eventTime = baseTime.minusMinutes(4),
                            newStatus = ApplicationStatus.PENDING,
                        )
                        .withEvent(
                            eventTime = baseTime.minusMinutes(1),
                            newStatus = ApplicationStatus.HANDLING,
                        )
                        .withEvent(
                            eventTime = baseTime.plusMinutes(2),
                            newStatus = ApplicationStatus.DECISION,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 25, *emptyArray())
                        .withEvent(
                            eventTime = baseTime.minusMinutes(3),
                            newStatus = ApplicationStatus.PENDING,
                        )
                        .withEvent(eventTime = baseTime, newStatus = ApplicationStatus.HANDLING)
                        .withEvent(
                            eventTime = baseTime.plusMinutes(3),
                            newStatus = ApplicationStatus.DECISION,
                        ),
                )
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            histories.forEach { history ->
                history.events.forEach { event ->
                    justRun { historyService.handleApplicationEvent(history.applicationId, event) }
                }
            }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                histories.forEach { history ->
                    history.events.forEach { event ->
                        historyService.handleApplicationEvent(history.applicationId, event)
                    }
                }
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `handles rapid status changes within short time window`() {
            val alluids = listOf(24)
            val baseTime = DEFAULT_EVENT_TIME
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withEvent(eventTime = baseTime, newStatus = ApplicationStatus.PENDING)
                    .withEvent(
                        eventTime = baseTime.plusSeconds(1),
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .withEvent(
                        eventTime = baseTime.plusSeconds(2),
                        newStatus = ApplicationStatus.RETURNED_TO_PREPARATION,
                    )
                    .withEvent(
                        eventTime = baseTime.plusSeconds(3),
                        newStatus = ApplicationStatus.PENDING,
                    )
                    .withEvent(
                        eventTime = baseTime.plusSeconds(4),
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .withEvent(
                        eventTime = baseTime.plusSeconds(5),
                        newStatus = ApplicationStatus.DECISION,
                    )
                    .asList()
            val events = histories.single().events
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            events.forEach { event -> justRun { historyService.handleApplicationEvent(24, event) } }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                events.forEach { event -> historyService.handleApplicationEvent(24, event) }
                historyService.setLastUpdateTime(any())
            }
        }
    }

    @Nested
    inner class EventValidationEdgeCases {

        @Test
        fun `handles events with extreme timestamp values`() {
            val alluids = listOf(24)
            val veryOldTime = ZonedDateTime.parse("1970-01-01T00:00:00Z")
            val veryFutureTime = ZonedDateTime.parse("2099-12-31T23:59:59Z")
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withEvent(eventTime = veryOldTime, newStatus = ApplicationStatus.PENDING)
                    .withEvent(eventTime = veryFutureTime, newStatus = ApplicationStatus.DECISION)
                    .asList()
            val events = histories.single().events
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            events.forEach { event -> justRun { historyService.handleApplicationEvent(24, event) } }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                events.forEach { event -> historyService.handleApplicationEvent(24, event) }
                historyService.setLastUpdateTime(any())
            }
        }
    }

    @Nested
    inner class ConcurrentEventProcessing {

        @Test
        fun `handles concurrent processing of different applications`() {
            val alluids = listOf(23, 24, 25)
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 23, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.PENDING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.HANDLING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 25, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.DECISION,
                        ),
                )
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            histories.forEach { history ->
                history.events.forEach { event ->
                    justRun { historyService.handleApplicationEvent(history.applicationId, event) }
                }
            }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                histories.forEach { history ->
                    history.events.forEach { event ->
                        historyService.handleApplicationEvent(history.applicationId, event)
                    }
                }
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `handles concurrent error and success processing`() {
            val alluids = listOf(23, 24)
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 23, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.PENDING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.HANDLING,
                        ),
                )
            val existingError =
                ApplicationHistoryFactory.createError(
                    alluId = 23,
                    eventTime = DEFAULT_EVENT_TIME.minusMinutes(1),
                    newStatus = ApplicationStatus.PENDING,
                )
            val successEvent = histories[1].events.first()
            every { historyService.getAllErrors() } returns listOf(existingError)
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            justRun { historyService.handleApplicationEvent(24, successEvent) }
            justRun {
                historyService.handleApplicationEvent(23, existingError.toApplicationStatusEvent())
            }
            justRun { historyService.deleteError(existingError) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                historyService.handleApplicationEvent(24, successEvent)
                historyService.setLastUpdateTime(any())
                historyService.handleApplicationEvent(23, existingError.toApplicationStatusEvent())
                historyService.deleteError(existingError)
            }
        }

        @Test
        fun `handles concurrent processing with mixed success and failure`() {
            val alluids = listOf(23, 24, 25)
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 23, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.PENDING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.HANDLING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 25, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.DECISION,
                        ),
                )
            val exception = RuntimeException("Processing error")
            val failingEvent = histories[1].events.first()
            val successEvent1 = histories[0].events.first()
            val successEvent2 = histories[2].events.first()
            val expectedError =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = failingEvent.eventTime,
                    newStatus = failingEvent.newStatus,
                    stackTrace = exception.stackTraceToString(),
                )
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            justRun { historyService.handleApplicationEvent(23, successEvent1) }
            every { historyService.handleApplicationEvent(24, failingEvent) } throws exception
            justRun { historyService.handleApplicationEvent(25, successEvent2) }
            justRun { historyService.saveErrors(listOf(expectedError)) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                historyService.handleApplicationEvent(23, successEvent1)
                historyService.handleApplicationEvent(24, failingEvent)
                historyService.handleApplicationEvent(25, successEvent2)
                historyService.saveErrors(listOf(expectedError))
                historyService.setLastUpdateTime(any())
            }
        }
    }

    @Nested
    inner class EventRollbackScenarios {

        @Test
        fun `handles partial processing failure with proper error storage`() {
            val alluids = listOf(24)
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(2),
                        newStatus = ApplicationStatus.PENDING,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(1),
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .withEvent(
                        eventTime = DEFAULT_EVENT_TIME,
                        newStatus = ApplicationStatus.DECISION,
                    )
                    .asList()
            val events = histories.single().events
            val failingEvent = events[1]
            val exception = RuntimeException("Processing failure")
            val expectedError =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = failingEvent.eventTime,
                    newStatus = failingEvent.newStatus,
                    stackTrace = exception.stackTraceToString(),
                )
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            justRun { historyService.handleApplicationEvent(24, events[0]) }
            every { historyService.handleApplicationEvent(24, failingEvent) } throws exception
            justRun { historyService.saveErrors(listOf(expectedError)) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                historyService.handleApplicationEvent(24, events[0])
                historyService.handleApplicationEvent(24, failingEvent)
                historyService.saveErrors(listOf(expectedError))
                historyService.setLastUpdateTime(any())
            }
        }

        @Test
        fun `handles recovery from previously failed event processing`() {
            val error =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = DEFAULT_EVENT_TIME,
                    newStatus = ApplicationStatus.PENDING,
                )
            val recoveredEvent = error.toApplicationStatusEvent()
            every { historyService.getAllErrors() } returns listOf(error)
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.handleApplicationEvent(24, recoveredEvent) }
            justRun { historyService.deleteError(error) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.handleApplicationEvent(24, recoveredEvent)
                historyService.deleteError(error)
            }
        }

        @Test
        fun `handles multiple application rollback scenarios`() {
            val errors =
                listOf(
                    ApplicationHistoryFactory.createError(
                        alluId = 23,
                        eventTime = DEFAULT_EVENT_TIME.minusMinutes(1),
                        newStatus = ApplicationStatus.PENDING,
                    ),
                    ApplicationHistoryFactory.createError(
                        alluId = 24,
                        eventTime = DEFAULT_EVENT_TIME,
                        newStatus = ApplicationStatus.HANDLING,
                    ),
                )
            val event1 = errors[0].toApplicationStatusEvent()
            val event2 = errors[1].toApplicationStatusEvent()
            val secondException = RuntimeException("Second failure")
            every { historyService.getAllErrors() } returns errors
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.handleApplicationEvent(23, event1) }
            justRun { historyService.deleteError(errors[0]) }
            every { historyService.handleApplicationEvent(24, event2) } throws secondException

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.handleApplicationEvent(23, event1)
                historyService.deleteError(errors[0])
                historyService.handleApplicationEvent(24, event2)
            }
        }

        @Test
        fun `handles cascading failures across multiple applications`() {
            val alluids = listOf(23, 24, 25)
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 23, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.PENDING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.HANDLING,
                        ),
                    ApplicationHistoryFactory.create(applicationId = 25, *emptyArray())
                        .withEvent(
                            eventTime = DEFAULT_EVENT_TIME,
                            newStatus = ApplicationStatus.DECISION,
                        ),
                )
            val exception1 = RuntimeException("First failure")
            val exception2 = RuntimeException("Second failure")
            val event1 = histories[0].events.first()
            val event2 = histories[1].events.first()
            val event3 = histories[2].events.first()
            val expectedErrors =
                listOf(
                    ApplicationHistoryFactory.createError(
                        alluId = 23,
                        eventTime = event1.eventTime,
                        newStatus = event1.newStatus,
                        stackTrace = exception1.stackTraceToString(),
                    ),
                    ApplicationHistoryFactory.createError(
                        alluId = 24,
                        eventTime = event2.eventTime,
                        newStatus = event2.newStatus,
                        stackTrace = exception2.stackTraceToString(),
                    ),
                )
            every { historyService.getAllErrors() } returns listOf()
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns
                OffsetDateTime.parse("2022-10-12T15:23:34.981654Z")
            every { alluClient.getApplicationStatusHistories(alluids, any()) } returns histories
            every { historyService.handleApplicationEvent(23, event1) } throws exception1
            every { historyService.handleApplicationEvent(24, event2) } throws exception2
            justRun { historyService.handleApplicationEvent(25, event3) }
            justRun { historyService.saveErrors(expectedErrors) }
            justRun { historyService.setLastUpdateTime(any()) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, any())
                historyService.handleApplicationEvent(23, event1)
                historyService.handleApplicationEvent(24, event2)
                historyService.handleApplicationEvent(25, event3)
                historyService.saveErrors(expectedErrors)
                historyService.setLastUpdateTime(any())
            }
        }
    }
}
