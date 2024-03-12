package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.test.Asserts.isValidBlobLocation
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class HankeAttachmentContentServiceITest(
    @Autowired private val attachmentContentService: HankeAttachmentContentService,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val attachmentFactory: HankeAttachmentFactory,
) : DatabaseTest() {
    private val hankeId = 1
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class Delete {
        @Test
        fun `Should delete the specified attachment content`() {
            val otherId = UUID.fromString("5824887b-ad50-48f8-bc08-0d5d3e8ba777")
            val attachment1 = HankeAttachmentFactory.createEntity(attachmentId)
            val attachment2 = HankeAttachmentFactory.createEntity(otherId)
            attachmentFactory.saveContentToCloud(attachment1.blobLocation, bytes = bytes)
            attachmentFactory.saveContentToCloud(attachment2.blobLocation, bytes = bytes)

            attachmentContentService.delete(attachment1.toDomain())

            val existingBlobs = fileClient.listBlobs(HANKE_LIITTEET).map { it.path }
            assertThat(existingBlobs).containsExactly(attachment2.blobLocation)
        }

        @Test
        fun `Should not throw an error even if content does not exist`() {
            val attachment = HankeAttachmentFactory.createEntity(attachmentId)

            attachmentContentService.delete(attachment.toDomain())

            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
        }
    }

    @Nested
    inner class Upload {
        @Test
        fun `Should return location of uploaded blob`() {
            val blobLocation =
                attachmentContentService.upload(
                    FILE_NAME_PDF,
                    APPLICATION_PDF,
                    DEFAULT_DATA,
                    hankeId
                )

            assertThat(blobLocation).isValidBlobLocation(id = hankeId)
        }
    }

    @Nested
    inner class Find {
        @Test
        fun `Should return the requested content`() {
            val attachment = HankeAttachmentFactory.createEntity(attachmentId)
            val otherId = UUID.fromString("d8ead4d2-5888-441e-bc0f-c4da4b634b70")
            val other = HankeAttachmentFactory.createEntity(otherId)
            attachmentFactory.saveContentToCloud(attachment.blobLocation, bytes = bytes)
            attachmentFactory.saveContentToCloud(other.blobLocation)

            val result = attachmentContentService.find(attachment.toDomain())

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `Should throw if attachment not found`() {
            val attachment = HankeAttachmentFactory.createEntity(attachmentId)

            assertFailure { attachmentContentService.find(attachment.toDomain()) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    hasMessage("Attachment not found, id=$attachmentId")
                }
        }
    }
}
