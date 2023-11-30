package fi.hel.haitaton.hanke.attachment.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import java.util.UUID
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@WithMockUser(USERNAME)
@ActiveProfiles("test")
@ExtendWith(MockFileClientExtension::class)
@SpringBootTest(
    properties =
        [
            "haitaton.attachment-migration.hanke.enabled=true",
            "haitaton.attachment-migration.hanke.initial-delay=60000" // prevent disturbance on test
        ]
)
class HankeAttachmentMigrationSchedulerITest(
    @Autowired private val attachmentFactory: HankeAttachmentFactory,
    @Autowired private val scheduler: HankeAttachmentMigrationScheduler,
    @Autowired private val fileClient: MockFileClient,
) : DatabaseTest() {
    @Test
    fun `Should migrate to cloud and remove old content`() {
        val attachment = attachmentFactory.save().withDbContent().value
        assertThat(attachment.blobLocation).isNull()
        assertThat(findContent(attachment.id)).isNotNull()

        scheduler.scheduleMigrate()

        val blobLocation = findAttachment(attachment.id).blobLocation() // has blob location
        assertThat(fileClient.download(HANKE_LIITTEET, blobLocation)).isNotNull() // blob is found
        assertThat(countOldContent()).isEqualTo(0) // old file deleted
    }

    private fun findContent(id: UUID?) =
        attachmentFactory.contentRepository.findByIdOrNull(id)
            ?: error("Content with id: '$id' missing.")

    private fun countOldContent() = attachmentFactory.contentRepository.count()

    private fun findAttachment(id: UUID?) =
        attachmentFactory.attachmentRepository.findByIdOrNull(id)
            ?: error("Attachment with id: '$id' missing.")
}

private fun HankeAttachmentEntity.blobLocation() =
    blobLocation ?: error("Blob location is not set.")
