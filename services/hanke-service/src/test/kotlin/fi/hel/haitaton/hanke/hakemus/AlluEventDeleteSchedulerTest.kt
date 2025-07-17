package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.configuration.LockService
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
import io.mockk.verifySequence
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry

class AlluEventDeleteSchedulerTest {
    private val historyService: HakemusHistoryService = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    private val alluEventDeletionScheduler =
        AlluEventDeletionScheduler(historyService, lockService, 90)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(historyService, jdbcLockRegistry)
    }

    @Test
    fun `does nothing if can't obtain lock`() {
        mockLocking(false)

        alluEventDeletionScheduler.deleteOldEvents()

        verifyOrder {
            jdbcLockRegistry.obtain(AlluEventDeletionScheduler.LOCK_NAME)
            historyService wasNot Called
        }
    }

    @Test
    fun `releases lock if there's an exception`() {
        val lock = mockLocking(true)
        every { historyService.deleteOldProcessedEvents(90) } throws Exception()

        alluEventDeletionScheduler.deleteOldEvents()

        verifySequence {
            jdbcLockRegistry.obtain(AlluEventDeletionScheduler.LOCK_NAME)
            lock.tryLock()
            historyService.deleteOldProcessedEvents(90)
            lock.unlock()
        }
    }

    @Test
    fun `releases lock if succeeds`() {
        val lock = mockLocking(true)
        justRun { historyService.deleteOldProcessedEvents(90) }

        alluEventDeletionScheduler.deleteOldEvents()

        verifySequence {
            jdbcLockRegistry.obtain(AlluEventDeletionScheduler.LOCK_NAME)
            lock.tryLock()
            historyService.deleteOldProcessedEvents(90)
            lock.unlock()
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock() } returns canObtainLock
        every { jdbcLockRegistry.obtain(AlluEventDeletionScheduler.LOCK_NAME) } returns mockLock
        return mockLock
    }
}
