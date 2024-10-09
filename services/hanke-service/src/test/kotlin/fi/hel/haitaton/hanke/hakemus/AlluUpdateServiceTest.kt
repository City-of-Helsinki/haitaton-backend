package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
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
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry
import org.springframework.web.reactive.function.client.WebClientResponseException

class AlluUpdateServiceTest {
    private val alluClient: AlluClient = mockk()
    private val historyService: HakemusHistoryService = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    private val alluUpdateService = AlluUpdateService(alluClient, historyService, lockService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(historyService, alluClient, jdbcLockRegistry)
    }

    @Nested
    inner class CheckApplicationStatuses {
        private val lastUpdatedString = "2022-10-12T14:14:58.423521Z"
        private val lastUpdated = OffsetDateTime.parse(lastUpdatedString)
        private val eventsAfter = ZonedDateTime.parse(lastUpdatedString)

        @Test
        fun `does nothing with no alluids`() {
            mockLocking(true)
            every { historyService.getAllAlluIds() } returns listOf()

            alluUpdateService.checkApplicationStatuses()

            verifySequence {
                jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME)
                historyService.getAllAlluIds()
                alluClient wasNot Called
            }
        }

        @Test
        fun `calls Allu with alluids and last update date`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { historyService.handleHakemusUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME)
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleHakemusUpdates(listOf(), any())
            }
        }

        @Test
        fun `calls application service with the returned histories`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            val histories = listOf(ApplicationHistoryFactory.create(applicationId = 24))
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { historyService.handleHakemusUpdates(histories, any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME)
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleHakemusUpdates(histories, any())
            }
        }

        @Test
        fun `calls application service with the current time`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { historyService.handleHakemusUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME)
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                historyService.handleHakemusUpdates(listOf(), withArg { assertThat(it).isRecent() })
            }
        }

        @Test
        fun `does nothing if can't obtain lock`() {
            mockLocking(false)

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME)
                alluClient wasNot Called
                historyService wasNot Called
            }
        }

        @Test
        fun `releases lock if there's an exception`() {
            val lock = mockLocking(true)
            val alluids = listOf(23, 24)
            every { historyService.getAllAlluIds() } returns alluids
            every { historyService.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } throws
                WebClientResponseException(500, "Internal server error", null, null, null)

            alluUpdateService.checkApplicationStatuses()

            verifySequence {
                jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME)
                lock.tryLock()
                historyService.getAllAlluIds()
                historyService.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                lock.unlock()
            }
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock() } returns canObtainLock
        every { jdbcLockRegistry.obtain(AlluUpdateService.LOCK_NAME) } returns mockLock
        return mockLock
    }
}
