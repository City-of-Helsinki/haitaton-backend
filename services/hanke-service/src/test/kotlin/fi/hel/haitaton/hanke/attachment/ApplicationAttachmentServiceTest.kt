package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentTransferService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentMetadataService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.configuration.LockService
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifyOrder
import java.util.concurrent.locks.Lock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.integration.jdbc.lock.JdbcLockRegistry

class ApplicationAttachmentServiceTest {
    private val cableReportService: CableReportService = mockk()
    private val metadataService: ApplicationAttachmentMetadataService = mockk()
    private val applicationRepository: ApplicationRepository = mockk()
    private val attachmentContentService: ApplicationAttachmentContentService = mockk()
    private val scanClient: FileScanClient = mockk()
    private val jdbcLockRegistry: JdbcLockRegistry = mockk()
    private val lockService = LockService(jdbcLockRegistry)
    private val contentTransferService: ApplicationAttachmentContentTransferService = mockk()

    private val applicationAttachmentService =
        ApplicationAttachmentService(
            cableReportService,
            metadataService,
            applicationRepository,
            attachmentContentService,
            scanClient,
            lockService,
            contentTransferService,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            cableReportService,
            metadataService,
            applicationRepository,
            attachmentContentService,
            scanClient,
            jdbcLockRegistry,
            contentTransferService,
        )
    }

    @Nested
    inner class TransferAttachmentToBlobStorage {
        @Test
        fun `does nothing if all content has been transferred`() {
            mockLocking(true)
            every { contentTransferService.nextTransferableAttachment() } returns null

            applicationAttachmentService.transferAttachmentContentToBlobStorage()

            verifyOrder {
                jdbcLockRegistry.obtain(applicationAttachmentService.lockName)
                contentTransferService.nextTransferableAttachment()
                attachmentContentService wasNot Called
            }
        }

        @Test
        fun `transfers content to blob and updates database`() {
            mockLocking(true)
            val attachmentEntity = ApplicationAttachmentFactory.createEntity()
            val blobPath = generateBlobPath(attachmentEntity.applicationId)
            every { contentTransferService.nextTransferableAttachment() } returns attachmentEntity
            every { contentTransferService.transferToBlob(attachmentEntity) } returns blobPath
            justRun { contentTransferService.cleanUpDatabase(attachmentEntity, blobPath) }

            applicationAttachmentService.transferAttachmentContentToBlobStorage()

            verifyOrder {
                jdbcLockRegistry.obtain(applicationAttachmentService.lockName)
                contentTransferService.nextTransferableAttachment()
                contentTransferService.transferToBlob(attachmentEntity)
                contentTransferService.cleanUpDatabase(attachmentEntity, blobPath)
            }
        }

        @Test
        fun `aborts if content is missing in database`() {
            mockLocking(true)
            val attachmentEntity = ApplicationAttachmentFactory.createEntity()
            every { contentTransferService.nextTransferableAttachment() } returns attachmentEntity
            every { contentTransferService.transferToBlob(attachmentEntity) } throws
                (AttachmentContentNotFoundException(
                    attachmentEntity.id!!,
                ))

            applicationAttachmentService.transferAttachmentContentToBlobStorage()

            verifyOrder {
                jdbcLockRegistry.obtain(applicationAttachmentService.lockName)
                contentTransferService.nextTransferableAttachment()
                contentTransferService.transferToBlob(attachmentEntity)
            }
        }

        @Test
        fun `deletes saved blob if database cleanup fails`() {
            mockLocking(true)
            val attachmentEntity = ApplicationAttachmentFactory.createEntity()
            val blobPath = generateBlobPath(attachmentEntity.applicationId)
            every { contentTransferService.nextTransferableAttachment() } returns attachmentEntity
            every { contentTransferService.transferToBlob(attachmentEntity) } returns blobPath
            every {
                contentTransferService.cleanUpDatabase(
                    attachmentEntity,
                    blobPath,
                )
            } throws (RuntimeException("database error"))
            justRun { attachmentContentService.delete(blobPath) }

            applicationAttachmentService.transferAttachmentContentToBlobStorage()

            verifyOrder {
                jdbcLockRegistry.obtain(applicationAttachmentService.lockName)
                contentTransferService.nextTransferableAttachment()
                contentTransferService.transferToBlob(attachmentEntity)
                contentTransferService.cleanUpDatabase(attachmentEntity, blobPath)
                attachmentContentService.delete(blobPath)
            }
        }

        @Test
        fun `does nothing if can't obtain lock`() {
            mockLocking(false)

            applicationAttachmentService.transferAttachmentContentToBlobStorage()

            verifyOrder {
                jdbcLockRegistry.obtain(applicationAttachmentService.lockName)
                contentTransferService wasNot Called
            }
        }

        @Test
        fun `releases lock if there's an exception`() {
            val lock = mockLocking(true)
            val attachmentEntity = ApplicationAttachmentFactory.createEntity()
            val blobPath = generateBlobPath(attachmentEntity.applicationId)
            every { contentTransferService.nextTransferableAttachment() } returns attachmentEntity
            every { contentTransferService.transferToBlob(attachmentEntity) } returns blobPath
            every {
                contentTransferService.cleanUpDatabase(
                    attachmentEntity,
                    blobPath,
                )
            } throws (RuntimeException("database error"))
            justRun { attachmentContentService.delete(blobPath) }

            applicationAttachmentService.transferAttachmentContentToBlobStorage()

            verifyOrder {
                jdbcLockRegistry.obtain(applicationAttachmentService.lockName)
                lock.tryLock()
                contentTransferService.nextTransferableAttachment()
                contentTransferService.transferToBlob(attachmentEntity)
                contentTransferService.cleanUpDatabase(attachmentEntity, blobPath)
                attachmentContentService.delete(blobPath)
                lock.unlock()
            }
        }
    }

    private fun mockLocking(canObtainLock: Boolean): Lock {
        val mockLock = mockk<Lock>(relaxUnitFun = true)
        every { mockLock.tryLock() } returns canObtainLock
        every { jdbcLockRegistry.obtain(applicationAttachmentService.lockName) } returns mockLock
        return mockLock
    }
}
