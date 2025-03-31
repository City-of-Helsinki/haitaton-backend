package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry

class HankeCompletionSchedulerTest {
    private val completionService: HankeCompletionService = mockk(relaxUnitFun = true)
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(completionService, jdbcLockRegistry)
    }

    @Nested
    inner class WithFeatureDisabled {
        private val featureFlags = FeatureFlags(mapOf(Feature.HANKE_COMPLETION to false))

        private val hankeCompletionScheduler =
            HankeCompletionScheduler(completionService, lockService, featureFlags)

        @Test
        fun `doesn't do anything`() {
            hankeCompletionScheduler.completeHankkeet()

            verify {
                jdbcLockRegistry wasNot Called
                completionService wasNot Called
            }
        }
    }

    @Nested
    inner class WithFeatureEnabled {
        private val featureFlags = FeatureFlags(mapOf(Feature.HANKE_COMPLETION to true))

        private val hankeCompletionScheduler =
            HankeCompletionScheduler(completionService, lockService, featureFlags)

        @Test
        fun `gets a list of hanke IDs and tries to complete them`() {
            mockLocking(true)
            every { completionService.getPublicIds() } returns listOf(1, 2, 3)

            hankeCompletionScheduler.completeHankkeet()

            verifySequence {
                jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                completionService.getPublicIds()
                completionService.completeHankeIfPossible(1)
                completionService.completeHankeIfPossible(2)
                completionService.completeHankeIfPossible(3)
            }
        }

        @Test
        fun `continues when a hanke has an validity error`() {
            mockLocking(true)
            every { completionService.getPublicIds() } returns listOf(1, 2, 3)
            every { completionService.completeHankeIfPossible(2) } throws
                HankealueWithoutEndDateException(HankeFactory.create(id = 2))

            hankeCompletionScheduler.completeHankkeet()

            verifySequence {
                jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                completionService.getPublicIds()
                completionService.completeHankeIfPossible(1)
                completionService.completeHankeIfPossible(2)
                completionService.completeHankeIfPossible(3)
            }
        }

        @Test
        fun `does nothing when lock can't be obtained after waiting`() {
            mockLocking(false)

            hankeCompletionScheduler.completeHankkeet()

            verifySequence {
                jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                completionService wasNot Called
            }
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock(10, TimeUnit.MINUTES) } returns canObtainLock
        every { jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME) } returns mockLock
        return mockLock
    }
}
