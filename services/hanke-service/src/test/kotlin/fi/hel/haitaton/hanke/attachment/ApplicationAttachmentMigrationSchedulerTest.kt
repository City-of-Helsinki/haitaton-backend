package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentMigrationScheduler
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentMigrationScheduler.Companion.LOCK_NAME
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentMigrator
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentWithContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentNotFoundException
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import java.util.UUID
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry

class ApplicationAttachmentMigrationSchedulerTest {
    private val migrator: ApplicationAttachmentMigrator = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService: LockService = LockService(jdbcLockRegistry)

    private val scheduler =
        ApplicationAttachmentMigrationScheduler(
            lockService,
            migrator,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            jdbcLockRegistry,
            migrator,
        )
    }

    @Nested
    inner class ScheduleMigrate {
        @Test
        fun `does nothing if all content has been migrated`() {
            mockLocking(true)
            every { migrator.findAttachmentWithDatabaseContent() } returns null

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                migrator.findAttachmentWithDatabaseContent()
            }
        }

        @Test
        fun `migrates content to blob and updates database`() {
            mockLocking(true)
            val applicationId = 123L
            val attachmentId = UUID.randomUUID()
            val blobPath = generateBlobPath(applicationId)
            val attachmentWithContent =
                ApplicationAttachmentWithContent(
                    id = attachmentId,
                    applicationId = applicationId,
                    content = ApplicationAttachmentFactory.createContent(),
                )
            every { migrator.findAttachmentWithDatabaseContent() } returns attachmentWithContent
            every { migrator.migrate(attachmentWithContent) } returns blobPath
            justRun { migrator.setBlobPathAndCleanup(attachmentId, blobPath) }

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                migrator.findAttachmentWithDatabaseContent()
                migrator.migrate(attachmentWithContent)
                migrator.setBlobPathAndCleanup(attachmentId, blobPath)
            }
        }

        @Test
        fun `aborts if content is missing in database`() {
            mockLocking(true)
            val attachmentId = UUID.randomUUID()
            every { migrator.findAttachmentWithDatabaseContent() } throws
                AttachmentContentNotFoundException(
                    attachmentId,
                )

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                migrator.findAttachmentWithDatabaseContent()
            }
        }

        @Test
        fun `aborts if blob upload fails`() {
            mockLocking(true)
            val applicationId = 123L
            val attachmentId = UUID.randomUUID()
            val attachmentWithContent =
                ApplicationAttachmentWithContent(
                    id = attachmentId,
                    applicationId = applicationId,
                    content = ApplicationAttachmentFactory.createContent(),
                )
            every { migrator.findAttachmentWithDatabaseContent() } returns attachmentWithContent
            every { migrator.migrate(attachmentWithContent) } throws
                RuntimeException("blob upload failed")

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                migrator.findAttachmentWithDatabaseContent()
                migrator.migrate(attachmentWithContent)
            }
        }

        @Test
        fun `reverts migrated blob content if database cleanup fails`() {
            mockLocking(true)
            val applicationId = 123L
            val attachmentId = UUID.randomUUID()
            val blobPath = generateBlobPath(applicationId)
            val attachmentWithContent =
                ApplicationAttachmentWithContent(
                    id = attachmentId,
                    applicationId = applicationId,
                    content = ApplicationAttachmentFactory.createContent(),
                )
            every { migrator.findAttachmentWithDatabaseContent() } returns attachmentWithContent
            every { migrator.migrate(attachmentWithContent) } returns blobPath
            every {
                migrator.setBlobPathAndCleanup(
                    attachmentId,
                    blobPath,
                )
            } throws RuntimeException("database error")
            justRun { migrator.revertMigration(blobPath) }

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                migrator.findAttachmentWithDatabaseContent()
                migrator.migrate(attachmentWithContent)
                migrator.setBlobPathAndCleanup(attachmentId, blobPath)
                migrator.revertMigration(blobPath)
            }
        }

        @Test
        fun `does nothing if can't obtain lock`() {
            mockLocking(false)

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                migrator wasNot Called
            }
        }

        @Test
        fun `releases lock if there's an exception`() {
            val lock = mockLocking(true)
            every { migrator.findAttachmentWithDatabaseContent() } throws RuntimeException("error")

            scheduler.scheduleMigrate()

            verifySequence {
                jdbcLockRegistry.obtain(LOCK_NAME)
                lock.tryLock()
                migrator.findAttachmentWithDatabaseContent()
                lock.unlock()
            }
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock() } returns canObtainLock
        every { jdbcLockRegistry.obtain(LOCK_NAME) } returns mockLock
        return mockLock
    }
}
