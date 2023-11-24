package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.first
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class HankeAttachmentContentServiceITest(
    @Autowired private val attachmentContentService: HankeAttachmentContentService,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val hankeAttachmentContentRepository: HankeAttachmentContentRepository,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
) : DatabaseTest() {

    private val hankeId = 1
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val path = "$hankeId/$attachmentId"
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class Delete {
        @Test
        fun `deletes the content when blobLocation is specified`() {
            hankeAttachmentFactory.saveContentToCloud(path, bytes = bytes)
            val attachmentEntity =
                HankeAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            attachmentContentService.delete(attachmentEntity)

            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `doesn't throw an error if content is missing`() {
            val attachmentEntity =
                HankeAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            attachmentContentService.delete(attachmentEntity)

            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `doesn't do anything if blobLocation is not specified`() {
            val attachmentEntity =
                hankeAttachmentFactory.save(blobLocation = null).withDbContent(bytes).value

            attachmentContentService.delete(attachmentEntity)

            val blobs = hankeAttachmentContentRepository.findAll()
            assertThat(blobs).hasSize(1)
            assertThat(blobs)
                .first()
                .prop(HankeAttachmentContentEntity::attachmentId)
                .isEqualTo(attachmentEntity.id)
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
                    DEFAULT_DATA,
                    hankeId
                )

            val idPart = blobLocation.substringBefore("/")
            val uuidPart = blobLocation.substringAfter("/")
            assertThat(idPart.toIntOrNull()).isEqualTo(hankeId)
            assertThat(UUID.fromString(uuidPart)).isNotNull()
        }
    }

    @Nested
    inner class Find {
        @Test
        fun `returns the content when blobLocation is specified`() {
            hankeAttachmentFactory.saveContentToCloud(path, bytes = bytes)
            val attachmentEntity =
                HankeAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            val result = attachmentContentService.find(attachmentEntity)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `returns the content when blobLocation is not specified`() {
            val attachmentEntity =
                hankeAttachmentFactory.save(blobLocation = null).withDbContent(bytes).value

            val result = attachmentContentService.find(attachmentEntity)

            assertThat(result).isEqualTo(bytes)
        }
    }

    @Nested
    inner class ReadFromFile {

        @Test
        fun `returns the right content`() {
            hankeAttachmentFactory.saveContentToCloud(path, bytes = bytes)

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
            val attachmentId = hankeAttachmentFactory.save().value.id!!
            hankeAttachmentFactory.saveContentToDb(attachmentId, bytes)

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
