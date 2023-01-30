package fi.hel.haitaton.hanke.application

import assertk.assertThat
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import io.mockk.Called
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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.integration.jdbc.lock.JdbcLockRegistry
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.WebClientResponseException

@ExtendWith(SpringExtension::class)
class AlluUpdateServiceTest {
    private val applicationRepository: ApplicationRepository = mockk()
    private val alluStatusRepository: AlluStatusRepository = mockk()
    private val cableReportService: CableReportService = mockk()
    private val applicationService: ApplicationService = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    private val alluUpdateService =
        AlluUpdateService(
            applicationRepository,
            alluStatusRepository,
            cableReportService,
            applicationService,
            lockService,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        confirmVerified(
            alluStatusRepository,
            applicationRepository,
            cableReportService,
            applicationService,
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
            every { applicationRepository.getAllAlluIds() } returns listOf()

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                applicationRepository.getAllAlluIds()
                alluStatusRepository wasNot Called
                cableReportService wasNot Called
                applicationService wasNot Called
            }
        }

        @Test
        fun `calls Allu with alluids and last update date`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { applicationService.handleApplicationUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                applicationService.handleApplicationUpdates(listOf(), any())
            }
        }

        @Test
        fun `calls application service with the returned histories`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            val histories = listOf(ApplicationHistoryFactory.create(applicationId = 24))
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { applicationService.handleApplicationUpdates(histories, any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                applicationService.handleApplicationUpdates(histories, any())
            }
        }

        @Test
        fun `calls application service with the current time`() {
            mockLocking(true)
            val alluids = listOf(23, 24)
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { applicationService.handleApplicationUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                applicationService.handleApplicationUpdates(
                    listOf(),
                    withArg { assertThat(it).isRecent() }
                )
            }
        }

        @Test
        fun `does nothing if can't obtain lock`() {
            mockLocking(false)

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                applicationRepository wasNot Called
                alluStatusRepository wasNot Called
                cableReportService wasNot Called
                applicationService wasNot Called
            }
        }

        @Test
        fun `releases lock if there's an exception`() {
            val lock = mockLocking(true)
            val alluids = listOf(23, 24)
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } throws
                WebClientResponseException(500, "Internal server error", null, null, null)

            alluUpdateService.checkApplicationStatuses()

            verifyOrder {
                jdbcLockRegistry.obtain(alluUpdateService.lockName)
                lock.tryLock()
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                lock.unlock()
                applicationService wasNot Called
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
