package fi.hel.haitaton.hanke.attachment.application

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.test.Asserts.isValidBlobLocation
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

class ApplicationAttachmentContentServiceITest(
    @Autowired private val attachmentContentService: ApplicationAttachmentContentService,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val fileClient: MockFileClient,
) : IntegrationTest() {

    private val applicationId = 1L
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class Delete {
        @Test
        fun `Should delete the specified attachment content`() {
            val otherId = UUID.fromString("5824887b-ad50-48f8-bc08-0d5d3e8ba777")
            val attachment1 = ApplicationAttachmentFactory.createEntity(attachmentId)
            val attachment2 = ApplicationAttachmentFactory.createEntity(otherId)
            attachmentFactory.saveContent(attachment1.blobLocation, bytes = bytes)
            attachmentFactory.saveContent(attachment2.blobLocation, bytes = bytes)

            attachmentContentService.delete(attachment1.blobLocation)

            val existingBlobs = fileClient.listBlobs(Container.HAKEMUS_LIITTEET).map { it.path }
            assertThat(existingBlobs).containsExactly(attachment2.blobLocation)
        }

        @Test
        fun `Should not throw an error even if content does not exist`() {
            val attachment = ApplicationAttachmentFactory.createEntity(attachmentId)

            attachmentContentService.delete(attachment.blobLocation)

            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
        }
    }

    @Nested
    inner class Upload {
        @Test
        fun `Should return location of uploaded blob`() {
            val blobLocation =
                attachmentContentService.upload(
                    FILE_NAME_PDF,
                    MediaType.APPLICATION_PDF,
                    PDF_BYTES,
                    applicationId
                )

            assertThat(blobLocation).isValidBlobLocation(applicationId)
        }
    }

    @Nested
    inner class Find {
        @Test
        fun `Should return the requested content`() {
            val attachment = ApplicationAttachmentFactory.createEntity(attachmentId)
            val otherId = UUID.fromString("d8ead4d2-5888-441e-bc0f-c4da4b634b70")
            val other = ApplicationAttachmentFactory.createEntity(otherId)
            attachmentFactory.saveContent(attachment.blobLocation, bytes = bytes)
            attachmentFactory.saveContent(other.blobLocation)

            val result = attachmentContentService.find(attachment.toDomain())

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `Should throw if attachment not found`() {
            val attachment = ApplicationAttachmentFactory.createEntity(attachmentId)

            assertFailure { attachmentContentService.find(attachment.toDomain()) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    hasMessage("Attachment not found, id=$attachmentId")
                }
        }
    }
}
