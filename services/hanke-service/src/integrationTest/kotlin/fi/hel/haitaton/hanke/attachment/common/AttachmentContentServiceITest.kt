package fi.hel.haitaton.hanke.attachment.common

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class AttachmentContentServiceITest : DatabaseTest() {

    @Autowired private lateinit var attachmentContentService: AttachmentContentService
    @Autowired
    private lateinit var hankeAttachmentContentRepository: HankeAttachmentContentRepository

    @Autowired private lateinit var hankeAttachmentRepository: HankeAttachmentRepository
    @Autowired lateinit var fileClient: MockFileClient
    @Autowired lateinit var hankeFactory: HankeFactory

    @BeforeEach
    fun beforeEach() {
        fileClient.recreateContainers()
    }

    private val container = Container.HANKE_LIITTEET
    private val hankeId = 1
    private val attachmentId = UUID.fromString("b820121e-ad54-4ab8-926a-c4a8193010b5")
    private val path = "$hankeId/$attachmentId"
    private val mediaType = MediaType.TEXT_PLAIN
    private val bytes = "Test content. Sisältää myös skandeja.".toByteArray()

    @Nested
    inner class ReadHankeAttachmentFromFile {

        @Test
        fun `returns the right content`() {
            fileClient.upload(container, path, "test.txt", mediaType, bytes)

            val result = attachmentContentService.readHankeAttachmentFromFile(path, attachmentId)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `throws AttachmentNotFoundException if attachment not found`() {
            assertFailure {
                    attachmentContentService.readHankeAttachmentFromFile(path, attachmentId)
                }
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
            val hanke = hankeFactory.saveEntity()
            val attachmentId =
                hankeAttachmentRepository
                    .save(
                        HankeAttachmentEntity(
                            id = null,
                            fileName = "test.txt",
                            contentType = MediaType.TEXT_PLAIN_VALUE,
                            createdByUserId = "User",
                            createdAt = OffsetDateTime.now(),
                            blobLocation = null,
                            hanke = hanke,
                        )
                    )
                    .id!!
            hankeAttachmentContentRepository.save(HankeAttachmentContentEntity(attachmentId, bytes))

            val result = attachmentContentService.readHankeAttachmentFromDatabase(attachmentId)

            assertThat(result).isEqualTo(bytes)
        }

        @Test
        fun `throws AttachmentNotFoundException if attachment not found`() {
            assertFailure { attachmentContentService.readHankeAttachmentFromDatabase(attachmentId) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    hasMessage("Attachment not found, id=$attachmentId")
                }
        }
    }
}
