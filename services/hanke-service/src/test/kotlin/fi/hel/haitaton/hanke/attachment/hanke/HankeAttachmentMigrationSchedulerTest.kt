package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.UnmigratedHankeAttachment
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentMigrationScheduler.Companion.MIGRATE_HANKE_ATTACHMENT
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import io.mockk.verifyOrder
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry

class HankeAttachmentMigrationSchedulerTest {
    private val migrator: HankeAttachmentMigrator = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)

    private val scheduler = HankeAttachmentMigrationScheduler(lockService, migrator)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            migrator,
            jdbcLockRegistry,
        )
    }

    @Test
    fun `Should migrate and cleanup if not migrated attachment is found`() {
        lockMock(obtain = true)
        val attachment = unMigratedAttachment()
        val blobPath = attachment.blobPath()
        every { migrator.findAttachmentWithDatabaseContent() } returns attachment
        every { migrator.migrate(attachment) } returns blobPath
        justRun { migrator.setBlobPathAndCleanup(attachment.id, blobPath) }

        scheduler.scheduleMigrate()

        verifyOrder {
            jdbcLockRegistry.obtain(MIGRATE_HANKE_ATTACHMENT)
            migrator.findAttachmentWithDatabaseContent()
            migrator.migrate(attachment)
            migrator.setBlobPathAndCleanup(attachment.id, blobPath)
        }
    }

    @Test
    fun `Should handle situations of no attachments to migrate`() {
        lockMock(obtain = true)
        every { migrator.findAttachmentWithDatabaseContent() } returns null

        scheduler.scheduleMigrate()

        verify { jdbcLockRegistry.obtain(MIGRATE_HANKE_ATTACHMENT) }
        verify { migrator.findAttachmentWithDatabaseContent() }
        verify(exactly = 0) { migrator.migrate(any()) }
        verify(exactly = 0) { migrator.setBlobPathAndCleanup(any(), any()) }
    }

    @Test
    fun `Should stop if lock is not obtained`() {
        lockMock(obtain = false)

        scheduler.scheduleMigrate()

        verifyAll {
            jdbcLockRegistry.obtain(MIGRATE_HANKE_ATTACHMENT)
            migrator wasNot Called
        }
    }

    private fun lockMock(obtain: Boolean): Lock =
        mockk<Lock>(relaxUnitFun = true).also {
            every { it.tryLock() } returns obtain
            every { jdbcLockRegistry.obtain(MIGRATE_HANKE_ATTACHMENT) } returns it
        }

    private fun unMigratedAttachment(): UnmigratedHankeAttachment {
        val hanke = HankeFactory.createEntity()
        val attachment = AttachmentFactory.hankeAttachmentEntity(hanke = hanke)
        val content = AttachmentFactory.hankeAttachmentContentEntity(attachmentId = attachment.id!!)

        return UnmigratedHankeAttachment(
            id = content.attachmentId,
            hankeId = hanke.id,
            content =
                AttachmentContent(
                    fileName = attachment.fileName,
                    contentType = attachment.contentType,
                    bytes = content.content
                )
        )
    }
}

private fun UnmigratedHankeAttachment.blobPath() =
    HankeAttachmentContentService.generateBlobPath(hankeId)
