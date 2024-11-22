package fi.hel.haitaton.hanke.attachment.taydennys

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.test.Asserts.isValidBlobLocation
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

class TaydennysAttachmentContentServiceITest(
    @Autowired private val attachmentContentService: TaydennysAttachmentContentService,
    @Autowired private val attachmentFactory: TaydennysAttachmentFactory,
    @Autowired private val fileClient: MockFileClient,
) : IntegrationTest() {

    private val taydennysId = UUID.randomUUID()
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class Delete {
        @Test
        fun `Should delete the specified attachment content`() {
            val otherId = UUID.fromString("5824887b-ad50-48f8-bc08-0d5d3e8ba777")
            val attachment1 = TaydennysAttachmentFactory.createEntity(attachmentId)
            val attachment2 = TaydennysAttachmentFactory.createEntity(otherId)
            attachmentFactory.saveContent(attachment1.blobLocation, bytes = bytes)
            attachmentFactory.saveContent(attachment2.blobLocation, bytes = bytes)

            attachmentContentService.delete(attachment1.blobLocation)

            val existingBlobs = fileClient.listBlobs(Container.HAKEMUS_LIITTEET).map { it.path }
            assertThat(existingBlobs).containsExactly(attachment2.blobLocation)
        }

        @Test
        fun `Should not throw an error even if content does not exist`() {
            val attachment = TaydennysAttachmentFactory.createEntity(attachmentId)

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
                    taydennysId,
                )

            assertThat(blobLocation).isValidBlobLocation(taydennysId)
        }
    }
}
