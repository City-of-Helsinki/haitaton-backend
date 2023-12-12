package fi.hel.haitaton.hanke.attachment.application

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class ApplicationAttachmentContentServiceITest(
    @Autowired private val attachmentContentService: ApplicationAttachmentContentService,
    @Autowired private val applicationAttachmentFactory: ApplicationAttachmentFactory,
) : DatabaseTest() {

    private val applicationId = 1L
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val path = "$applicationId/$attachmentId"
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class Find {
        @Test
        fun `returns the content when blobLocation is specified`() {
            applicationAttachmentFactory.saveContentToCloud(path, bytes = bytes)
            val attachmentEntity =
                ApplicationAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            val result = attachmentContentService.find(attachmentEntity.toDomain())

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `returns the content when blobLocation is not specified`() {
            val attachmentEntity =
                applicationAttachmentFactory.save(blobLocation = null).withDbContent(bytes).value

            val result = attachmentContentService.find(attachmentEntity.toDomain())

            assertThat(result).isEqualTo(bytes)
        }
    }

    @Nested
    inner class ReadFromFile {

        @Test
        fun `returns the right content`() {
            applicationAttachmentFactory.saveContentToCloud(path, bytes = bytes)

            val result = attachmentContentService.readFromFile(path, attachmentId)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `throws AttachmentNotFoundException if attachment not found`() {
            assertFailure { attachmentContentService.readFromFile(path, attachmentId) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    hasMessage("Attachment not found, id=$attachmentId")
                }
        }
    }

    @Nested
    inner class ReadFromDatabase {
        @Test
        fun `returns the right content`() {
            val attachmentId = applicationAttachmentFactory.save().value.id!!
            applicationAttachmentFactory.saveContentToDb(attachmentId, bytes)

            val result = attachmentContentService.readFromDatabase(attachmentId)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `throws AttachmentNotFoundException if attachment not found`() {
            assertFailure { attachmentContentService.readFromDatabase(attachmentId) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    hasMessage("Attachment not found, id=$attachmentId")
                }
        }
    }
}
