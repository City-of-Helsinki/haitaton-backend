package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.domain.HankeReminder
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

        @Nested
        inner class CompleteHankkeet {
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
        inner class SendCompletionReminders {
            @Test
            fun `doesn't do anything`() {
                hankeCompletionScheduler.sendCompletionReminders()

                verify {
                    jdbcLockRegistry wasNot Called
                    completionService wasNot Called
                }
            }
        }

        @Nested
        inner class DeleteCompletedHanke {
            @Test
            fun `doesn't do anything`() {
                hankeCompletionScheduler.deleteCompletedHanke()

                verify {
                    jdbcLockRegistry wasNot Called
                    completionService wasNot Called
                }
            }
        }

        @Nested
        inner class SendDeletionReminders {
            @Test
            fun `doesn't do anything`() {
                hankeCompletionScheduler.sendDeletionReminders()

                verify {
                    jdbcLockRegistry wasNot Called
                    completionService wasNot Called
                }
            }
        }
    }

    @Nested
    inner class WithFeatureEnabled {
        private val featureFlags = FeatureFlags(mapOf(Feature.HANKE_COMPLETION to true))

        private val hankeCompletionScheduler =
            HankeCompletionScheduler(completionService, lockService, featureFlags)

        @Nested
        inner class CompleteHankkeet {

            @Test
            fun `gets a list of hanke IDs and tries to complete them`() {
                mockLocking(true)
                every { completionService.idsToComplete() } returns listOf(1, 2, 3)

                hankeCompletionScheduler.completeHankkeet()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsToComplete()
                    completionService.completeHankeIfPossible(1)
                    completionService.completeHankeIfPossible(2)
                    completionService.completeHankeIfPossible(3)
                }
            }

            @Test
            fun `continues when a hanke has an validity error`() {
                mockLocking(true)
                every { completionService.idsToComplete() } returns listOf(1, 2, 3)
                every { completionService.completeHankeIfPossible(2) } throws
                    HankealueWithoutEndDateException(HankeFactory.create(id = 2))

                hankeCompletionScheduler.completeHankkeet()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsToComplete()
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

        @Nested
        inner class SendCompletionReminders {
            @Test
            fun `gets a list of hanke IDs for each reminder and tries to send their reminders`() {
                mockLocking(true)
                every { completionService.idsForReminders(any()) } returns listOf(1, 2, 3)

                hankeCompletionScheduler.sendCompletionReminders()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsForReminders(HankeReminder.COMPLETION_14)
                    completionService.sendReminderIfNecessary(1, HankeReminder.COMPLETION_14)
                    completionService.sendReminderIfNecessary(2, HankeReminder.COMPLETION_14)
                    completionService.sendReminderIfNecessary(3, HankeReminder.COMPLETION_14)
                    completionService.idsForReminders(HankeReminder.COMPLETION_5)
                    completionService.sendReminderIfNecessary(1, HankeReminder.COMPLETION_5)
                    completionService.sendReminderIfNecessary(2, HankeReminder.COMPLETION_5)
                    completionService.sendReminderIfNecessary(3, HankeReminder.COMPLETION_5)
                }
            }

            @Test
            fun `continues when a hanke has an validity error`() {
                mockLocking(true)
                every { completionService.idsForReminders(any()) } returns listOf(1, 2, 3)
                every {
                    completionService.sendReminderIfNecessary(2, HankeReminder.COMPLETION_14)
                } throws HankealueWithoutEndDateException(HankeFactory.create(id = 2))

                hankeCompletionScheduler.sendCompletionReminders()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsForReminders(HankeReminder.COMPLETION_14)
                    completionService.sendReminderIfNecessary(1, HankeReminder.COMPLETION_14)
                    completionService.sendReminderIfNecessary(2, HankeReminder.COMPLETION_14)
                    completionService.sendReminderIfNecessary(3, HankeReminder.COMPLETION_14)
                    completionService.idsForReminders(HankeReminder.COMPLETION_5)
                    completionService.sendReminderIfNecessary(1, HankeReminder.COMPLETION_5)
                    completionService.sendReminderIfNecessary(2, HankeReminder.COMPLETION_5)
                    completionService.sendReminderIfNecessary(3, HankeReminder.COMPLETION_5)
                }
            }

            @Test
            fun `does nothing when lock can't be obtained after waiting`() {
                mockLocking(false)

                hankeCompletionScheduler.sendCompletionReminders()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService wasNot Called
                }
            }
        }

        @Nested
        inner class DeleteCompletedHanke {
            @Test
            fun `gets a list of hanke IDs to delete and attempts to delete them`() {
                mockLocking(true)
                every { completionService.idsToDelete() } returns listOf(1, 2, 3)

                hankeCompletionScheduler.deleteCompletedHanke()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsToDelete()
                    completionService.deleteHanke(1)
                    completionService.deleteHanke(2)
                    completionService.deleteHanke(3)
                }
            }

            @Test
            fun `continues when a hanke has an validity error`() {
                mockLocking(true)
                every { completionService.idsToDelete() } returns listOf(1, 2, 3)
                every { completionService.deleteHanke(2) } throws
                    HankeHasNoCompletionDateException(HankeFactory.createEntity())

                hankeCompletionScheduler.deleteCompletedHanke()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsToDelete()
                    completionService.deleteHanke(1)
                    completionService.deleteHanke(2)
                    completionService.deleteHanke(3)
                }
            }

            @Test
            fun `does nothing when lock can't be obtained after waiting`() {
                mockLocking(false)

                hankeCompletionScheduler.deleteCompletedHanke()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService wasNot Called
                }
            }
        }

        @Nested
        inner class SendDeletionReminders {
            @Test
            fun `gets a list of hanke IDs for each reminder and tries to send their reminders`() {
                mockLocking(true)
                every { completionService.idsForDeletionReminders() } returns listOf(1, 2, 3)

                hankeCompletionScheduler.sendDeletionReminders()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsForDeletionReminders()
                    completionService.sendDeletionRemindersIfNecessary(1)
                    completionService.sendDeletionRemindersIfNecessary(2)
                    completionService.sendDeletionRemindersIfNecessary(3)
                }
            }

            @Test
            fun `continues when a hanke has an validity error`() {
                mockLocking(true)
                every { completionService.idsForDeletionReminders() } returns listOf(1, 2, 3)
                every { completionService.sendDeletionRemindersIfNecessary(2) } throws
                    HankeNotCompletedException(HankeFactory.createEntity(mockId = 2))

                hankeCompletionScheduler.sendDeletionReminders()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService.idsForDeletionReminders()
                    completionService.sendDeletionRemindersIfNecessary(1)
                    completionService.sendDeletionRemindersIfNecessary(2)
                    completionService.sendDeletionRemindersIfNecessary(3)
                }
            }

            @Test
            fun `does nothing when lock can't be obtained after waiting`() {
                mockLocking(false)

                hankeCompletionScheduler.sendDeletionReminders()

                verifySequence {
                    jdbcLockRegistry.obtain(HankeCompletionScheduler.LOCK_NAME)
                    completionService wasNot Called
                }
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
