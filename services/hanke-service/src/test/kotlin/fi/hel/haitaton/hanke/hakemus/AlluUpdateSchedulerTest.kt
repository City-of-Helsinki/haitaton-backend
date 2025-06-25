package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.configuration.LockService
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import io.mockk.verifySequence
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry

class AlluUpdateSchedulerTest {
    private val alluUpdateService: AlluUpdateService = mockk()
    private val historyService: HakemusHistoryService = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    private val alluUpdateScheduler = AlluUpdateScheduler(alluUpdateService, lockService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluUpdateService, historyService, jdbcLockRegistry)
    }

    @Test
    fun `does nothing if can't obtain lock`() {
        mockLocking(false)

        alluUpdateScheduler.checkApplicationHistories()

        verifyOrder {
            jdbcLockRegistry.obtain(AlluUpdateScheduler.LOCK_NAME)
            alluUpdateService wasNot Called
            historyService wasNot Called
        }
    }

    @Test
    fun `releases lock if there's an exception`() {
        val lock = mockLocking(true)
        every { alluUpdateService.handleUpdates() } throws Exception()

        alluUpdateScheduler.checkApplicationHistories()

        verifySequence {
            jdbcLockRegistry.obtain(AlluUpdateScheduler.LOCK_NAME)
            lock.tryLock()
            alluUpdateService.handleUpdates()
            lock.unlock()
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock() } returns canObtainLock
        every { jdbcLockRegistry.obtain(AlluUpdateScheduler.LOCK_NAME) } returns mockLock
        return mockLock
    }
}
