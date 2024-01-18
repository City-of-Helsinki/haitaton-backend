package fi.hel.haitaton.hanke.attachment.application

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.startsWith
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@ExtendWith(MockFileClientExtension::class)
@SpringBootTest(
    properties =
        [
            "haitaton.attachment-migration.application.enabled=true",
            "haitaton.attachment-migration.application.initial-delay=60000"
        ]
)
class ApplicationAttachmentMigrationSchedulerITest(
    @Autowired private val attachmentMigrationScheduler: ApplicationAttachmentMigrationScheduler,
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

    @Test
    fun `does nothing if all content has been migrated`() {
        val attachment = attachmentFactory.save().withCloudContent().value
        assertThat(attachmentRepository.findAll()).hasSize(1)
        assertThat(contentRepository.findAll()).isEmpty()
        assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(1)
        attachmentRepository.findById(attachment.id!!).get().apply {
            assertThat(blobLocation).isNotNull()
            assertThat(blobLocation!!).startsWith("${attachment.applicationId}/")
        }

        attachmentMigrationScheduler.scheduleMigrate()

        assertThat(attachmentRepository.findAll()).hasSize(1)
        assertThat(contentRepository.findAll()).isEmpty()
        assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(1)
        attachmentRepository.findById(attachment.id!!).get().apply {
            assertThat(blobLocation).isNotNull()
            assertThat(blobLocation!!).startsWith("${attachment.applicationId}/")
        }
    }

    @Test
    fun `migrates content to blob and updates database`() {
        val attachment = attachmentFactory.save().withDbContent().value
        assertThat(attachmentRepository.findAll()).hasSize(1)
        assertThat(contentRepository.findAll()).hasSize(1)
        assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()

        attachmentMigrationScheduler.scheduleMigrate()

        assertThat(attachmentRepository.findAll()).hasSize(1)
        assertThat(contentRepository.findAll()).isEmpty()
        assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).hasSize(1)
        attachmentRepository.findById(attachment.id!!).get().apply {
            assertThat(blobLocation).isNotNull()
            assertThat(blobLocation!!).startsWith("${attachment.applicationId}/")
        }
    }

    @Test
    fun `aborts if content is missing in database`() {
        val attachment = attachmentFactory.save().value
        assertThat(attachmentRepository.findAll()).hasSize(1)
        assertThat(contentRepository.findAll()).isEmpty()
        assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
        attachmentRepository.findById(attachment.id!!).get().apply {
            assertThat(blobLocation).isNull()
        }

        attachmentMigrationScheduler.scheduleMigrate()

        assertThat(attachmentRepository.findAll()).hasSize(1)
        assertThat(contentRepository.findAll()).isEmpty()
        assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
        attachmentRepository.findById(attachment.id!!).get().apply {
            assertThat(blobLocation).isNull()
        }
    }
}
