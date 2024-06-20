package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
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
    private val hakemusRepository: HakemusRepository = mockk()
    private val alluStatusRepository: AlluStatusRepository = mockk()
    private val alluClient: AlluClient = mockk()
    private val hakemusService: HakemusService = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    private val alluUpdateService =
        AlluUpdateService(
            hakemusRepository,
            alluStatusRepository,
            alluClient,
            hakemusService,
            lockService,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            alluStatusRepository,
            hakemusRepository,
            alluClient,
            hakemusService,
            jdbcLockRegistry,
        )
    }

    @Nested
    inner class CheckApplicationStatuses {
        private val lastUpdatedString = "2022-10-12T14:14:58.423521Z"
        private val lastUpdated = OffsetDateTime.parse(lastUpdatedString)
        private val eventsAfter = ZonedDateTime.parse(lastUpdatedString)

        @Test
        fun `does nothing with no alluids`() {
            mockLocking(true)
            every { hakemusRepository.getAllAlluIds() } returns listOf()

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                hakemusRepository.getAllAlluIds()
                alluStatusRepository wasNot Called
                alluClient wasNot Called
                hakemusService wasNot Called
            }
        }

        @Test
        fun `calls Allu with alluids and last update date`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            every { hakemusRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { hakemusService.handleHakemusUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                hakemusRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                hakemusService.handleHakemusUpdates(listOf(), any())
            }
        }

        @Test
        fun `calls application service with the returned histories`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            val histories = listOf(ApplicationHistoryFactory.create(applicationId = 24))
            every { hakemusRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { hakemusService.handleHakemusUpdates(histories, any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                hakemusRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                hakemusService.handleHakemusUpdates(histories, any())
            }
        }

        @Test
        fun `calls application service with the current time`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            every { hakemusRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { hakemusService.handleHakemusUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                hakemusRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                hakemusService.handleHakemusUpdates(listOf(), withArg { assertThat(it).isRecent() })
            }
        }

        @Test
        fun `does nothing if can't obtain lock`() {
            mockLocking(false)

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                hakemusRepository wasNot Called
                alluStatusRepository wasNot Called
                alluClient wasNot Called
                hakemusService wasNot Called
            }
        }

        @Test
        fun `releases lock if there's an exception`() {
            val lock = mockLocking(true)
            val alluids = listOf(23, 24)
            every { hakemusRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { alluClient.getApplicationStatusHistories(alluids, eventsAfter) } throws
                WebClientResponseException(500, "Internal server error", null, null, null)

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                lock.tryLock()
                hakemusRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                alluClient.getApplicationStatusHistories(alluids, eventsAfter)
                lock.unlock()
                hakemusService wasNot Called
            }
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock() } returns canObtainLock
        every { jdbcLockRegistry.obtain(alluUpdateService.lockName) } returns mockLock
        return mockLock
    }
}
