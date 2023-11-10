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
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeAttachmentContentServiceITest : DatabaseTest(), HankeAttachmentFactory {

    @Autowired private lateinit var attachmentContentService: HankeAttachmentContentService
    @Autowired override lateinit var fileClient: MockFileClient
    @Autowired override lateinit var hankeAttachmentRepository: HankeAttachmentRepository
    @Autowired
    override lateinit var hankeAttachmentContentRepository: HankeAttachmentContentRepository
    @Autowired override lateinit var hankeFactory: HankeFactory

    @BeforeEach
    fun beforeEach() {
        fileClient.recreateContainers()
    }

    private val hankeId = 1
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val path = "$hankeId/$attachmentId"
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class Delete {
        @Test
        fun `deletes the content when blobLocation is specified`() {
            saveContentToCloud(path, bytes = bytes)
            val attachmentEntity =
                HankeAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            attachmentContentService.delete(attachmentEntity)

            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `doesn't throw an error if content is missing`() {
            val attachmentEntity =
                HankeAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            attachmentContentService.delete(attachmentEntity)

            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `doesn't do anything if blobLocation is not specified`() {
            val attachmentEntity = saveAttachment(blobLocation = null).withDbContent(bytes)

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
    inner class FindHankeContent {
        @Test
        fun `returns the content when blobLocation is specified`() {
            saveContentToCloud(path, bytes = bytes)
            val attachmentEntity =
                HankeAttachmentFactory.createEntity(attachmentId, blobLocation = path)

            val result = attachmentContentService.find(attachmentEntity)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `returns the content when blobLocation is not specified`() {
            val attachmentEntity = saveAttachment(blobLocation = null)
            saveContentToDb(attachmentEntity.id!!, bytes)

            val result = attachmentContentService.find(attachmentEntity)

            assertThat(result).isEqualTo(bytes)
        }
    }

    @Nested
    inner class ReadHankeAttachmentFromFile {

        @Test
        fun `returns the right content`() {
            saveContentToCloud(path, bytes = bytes)

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
    inner class ReadHankeAttachmentFromDatabase {
        @Test
        fun `returns the right content`() {
            val attachmentId = saveAttachment().id!!
            saveContentToDb(attachmentId, bytes)

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
