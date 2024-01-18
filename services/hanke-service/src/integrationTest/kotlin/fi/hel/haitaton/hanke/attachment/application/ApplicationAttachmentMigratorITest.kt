package fi.hel.haitaton.hanke.attachment.application

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TestFile
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ApplicationAttachmentMigratorITest(
    @Autowired private val attachmentMigrator: ApplicationAttachmentMigrator,
    @Autowired private val attachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val contentRepository: ApplicationAttachmentContentRepository,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val fileClient: MockFileClient,
) : DatabaseTest() {

    @BeforeEach
    fun setup() {
        clearAllMocks()
        fileClient.recreateContainers()
    }

    @AfterEach
    fun tearDown() {
        checkUnnecessaryStub()
    }

    @Nested
    @Transactional
    inner class FindAttachmentWithDatabaseContent {
        @Test
        fun `Should return null if no attachments in content table`() {
            val attachmentWithContent = attachmentMigrator.findAttachmentWithDatabaseContent()

            assertThat(attachmentWithContent).isNull()
        }

        @Test
        fun `Should return un-migrated content when there are any`() {
            val attachment = attachmentFactory.save().withDbContent().value
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).hasSize(1)
            val content = contentRepository.findByIdOrNull(attachment.id!!)!!
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()

            val attachmentWithContent = attachmentMigrator.findAttachmentWithDatabaseContent()

            assertThat(attachmentWithContent).isNotNull().all {
                prop(ApplicationAttachmentWithContent::id).isEqualTo(attachment.id!!)
                prop(ApplicationAttachmentWithContent::applicationId)
                    .isEqualTo(attachment.applicationId)
                prop(ApplicationAttachmentWithContent::content).all {
                    prop(AttachmentContent::fileName).isEqualTo(attachment.fileName)
                    prop(AttachmentContent::contentType).isEqualTo(attachment.contentType)
                    prop(AttachmentContent::bytes).isEqualTo(content.content)
                }
            }
        }
    }

    @Nested
    inner class Migrate {
        @Test
        fun `Should migrate content to blob`() {
            attachmentFactory.save().withDbContent().value
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).hasSize(1)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
            val attachmentWithContent = attachmentMigrator.findAttachmentWithDatabaseContent()!!

            val blobPath = attachmentMigrator.migrate(attachmentWithContent)

            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).hasSize(1)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(1)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET).first()).all {
                prop(TestFile::path).isEqualTo(blobPath)
            }
        }
    }

    @Nested
    @Transactional
    inner class SetBlobPathAndCleanup {
        @Test
        fun `Should throw AttachmentNotFoundException if attachment is not in db`() {
            val attachment = attachmentFactory.save().withDbContent().value
            val blobPath = generateBlobPath(attachment.applicationId)
            attachmentRepository.delete(attachment)
            assertThat(attachmentRepository.findAll()).isEmpty()

            assertThrows<AttachmentNotFoundException> {
                attachmentMigrator.setBlobPathAndCleanup(attachment.id!!, blobPath)
            }

            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Should set blob path and delete content from db`() {
            val attachment = attachmentFactory.save().withDbContent().value
            val blobPath = generateBlobPath(attachment.applicationId)
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).hasSize(1)

            attachmentMigrator.setBlobPathAndCleanup(attachment.id!!, blobPath)

            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(attachmentRepository.getReferenceById(attachment.id!!)).all {
                prop(ApplicationAttachmentEntity::blobLocation).isEqualTo(blobPath)
            }
            assertThat(contentRepository.findAll()).isEmpty()
        }
    }
}
