package fi.hel.haitaton.hanke.hakemus

import assertk.assertFailure
import assertk.assertions.hasClass
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.DEFAULT_EVENT_TIME
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.asList
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withDefaultEvents
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withEvent
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
    fun `updates only last update time when no alluIds or errors`() {
        every { historyService.getAllErrors() } returns listOf()
        every { historyService.getAllAlluIds() } returns listOf()
        justRun { historyService.setLastUpdateTime(any()) }

        alluUpdateService.handleUpdates()

        verifySequence {
            historyService.getAllErrors()
            historyService.getAllAlluIds()
            historyService.setLastUpdateTime(any())
            alluClient wasNot Called
        }
    }

    @Nested
    inner class HandleNormalUpdates {
        private val lastUpdatedString = "2022-10-12T15:25:34.981654Z"
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
    }

    @Nested
    inner class HandleErrors {

        @Test
        fun `handles errors and deletes them`() {
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withDefaultEvents()
                    .asList()
            val error1 =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = histories.single().events.first().eventTime,
                )
            val error2 =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = histories.single().events.last().eventTime,
                )
            val event1 = histories.single().events.first()
            val event2 = histories.single().events.last()
            every { historyService.getAllErrors() } returns listOf(error1, error2)
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.setLastUpdateTime(any()) }
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(24),
                    error1.eventTime.minusSeconds(1),
                )
            } returns histories
            justRun { historyService.handleApplicationEvent(24, event1) }
            justRun { historyService.deleteError(error1) }
            justRun { historyService.handleApplicationEvent(24, event2) }
            justRun { historyService.deleteError(error2) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.setLastUpdateTime(any())
                alluClient.getApplicationStatusHistories(
                    listOf(24),
                    error1.eventTime.minusSeconds(1),
                )
                historyService.handleApplicationEvent(24, event1)
                historyService.deleteError(error1)
                historyService.handleApplicationEvent(24, event2)
                historyService.deleteError(error2)
            }
        }

        @Test
        fun `throws exception if Allu has no history for a past error`() {
            val histories =
                ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                    .withDefaultEvents()
                    .asList()
            val error1 =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = histories.single().events.first().eventTime,
                )
            val error2 =
                ApplicationHistoryFactory.createError(
                    alluId = 24,
                    eventTime = histories.single().events.last().eventTime,
                )
            every { historyService.getAllErrors() } returns listOf(error1, error2)
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.setLastUpdateTime(any()) }
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(24),
                    error1.eventTime.minusSeconds(1),
                )
            } returns emptyList()

            val failure = assertFailure { alluUpdateService.handleUpdates() }

            failure.hasClass(IllegalStateException::class)
            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.setLastUpdateTime(any())
                alluClient.getApplicationStatusHistories(
                    listOf(24),
                    error1.eventTime.minusSeconds(1),
                )
            }
        }

        @Test
        fun `skips new history when going through errors`() {
            val eventTime = DEFAULT_EVENT_TIME
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(applicationId = 24)
                        .withEvent(eventTime = eventTime),
                    ApplicationHistoryFactory.create(applicationId = 25)
                        .withEvent(eventTime = eventTime.plusSeconds(1)),
                    ApplicationHistoryFactory.create(applicationId = 26)
                        .withEvent(eventTime = eventTime.plusSeconds(2)),
                )
            val error = ApplicationHistoryFactory.createError(alluId = 24, eventTime = eventTime)
            every { historyService.getAllErrors() } returns listOf(error)
            every { historyService.getAllAlluIds() } returns listOf()
            justRun { historyService.setLastUpdateTime(any()) }
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(24),
                    error.eventTime.minusSeconds(1),
                )
            } returns histories
            justRun { historyService.handleApplicationEvent(24, histories[0].events.first()) }
            justRun { historyService.deleteError(error) }

            alluUpdateService.handleUpdates()

            verifyOrder {
                historyService.getAllErrors()
                historyService.getAllAlluIds()
                historyService.setLastUpdateTime(any())
                alluClient.getApplicationStatusHistories(
                    listOf(24),
                    error.eventTime.minusSeconds(1),
                )
                historyService.handleApplicationEvent(24, histories[0].events.first())
                historyService.deleteError(error)
            }
        }
    }
}
