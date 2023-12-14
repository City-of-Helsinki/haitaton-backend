package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeIdentifierFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeAttachmentServiceITests(
    @Autowired private val hankeAttachmentService: HankeAttachmentService,
    @Autowired private val hankeAttachmentRepository: HankeAttachmentRepository,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
) : DatabaseTest() {

    @Nested
    inner class GetMetadataList {
        @Test
        fun `getMetadataList should return related metadata list`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            (1..2).forEach { _ ->
                hankeAttachmentRepository.save(
                    HankeAttachmentEntity(
                        id = null,
                        fileName = FILE_NAME_PDF,
                        contentType = APPLICATION_PDF_VALUE,
                        createdByUserId = USERNAME,
                        createdAt = OffsetDateTime.now(),
                        blobLocation = "${hanke.id}/${UUID.randomUUID()}",
                        hanke = hanke,
                    )
                )
            }

            val result = hankeAttachmentService.getMetadataList(hanke.hankeTunnus)

            assertThat(result).hasSize(2)
            assertThat(result).each { d ->
                d.transform { it.id }.isNotNull()
                d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
                d.transform { it.createdByUserId }.isEqualTo(USERNAME)
                d.transform { it.createdAt }.isNotNull()
                d.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus)
            }
        }
    }

    @Nested
    @ExtendWith(MockFileClientExtension::class)
    inner class GetContent {
        private val path = "in/cloud"

        @Test
        fun `throws exception if attachment not found`() {
            val attachmentId = UUID.fromString("93b5c49d-918a-453d-a2bf-b918b47923c1")

            val failure = assertFailure { hankeAttachmentService.getContent(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Test
        fun `returns the attachment content, filename and content type`() {
            val attachment = hankeAttachmentFactory.save().withCloudContent(path).value

            val result = hankeAttachmentService.getContent(attachment.id!!)

            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
        }
    }

    @Nested
    inner class SaveAttachment {
        @Test
        fun `Should return metadata of saved attachment`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val blobPath = blobPath(hanke.id)

            val result =
                hankeAttachmentService.saveAttachment(
                    hankeTunnus = hanke.hankeTunnus,
                    name = FILE_NAME_PDF,
                    type = APPLICATION_PDF_VALUE,
                    blobPath = blobPath,
                )

            assertThat(result).all {
                prop(HankeAttachmentMetadataDto::id).isNotNull()
                prop(HankeAttachmentMetadataDto::createdByUserId).isEqualTo(USERNAME)
                prop(HankeAttachmentMetadataDto::fileName).isEqualTo(FILE_NAME_PDF)
                prop(HankeAttachmentMetadataDto::createdAt).isRecent()
                prop(HankeAttachmentMetadataDto::hankeTunnus).isEqualTo(hanke.hankeTunnus)
            }
            val attachments = hankeAttachmentRepository.findAll()
            assertThat(attachments).hasSize(1)
            assertThat(attachments.first()).all {
                prop(HankeAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
                prop(HankeAttachmentEntity::fileName).isEqualTo(FILE_NAME_PDF)
                prop(HankeAttachmentEntity::createdAt).isRecent()
                prop(HankeAttachmentEntity::blobLocation).isEqualTo(blobPath)
            }
        }

        @Test
        fun `Should throw if attachment amount is exceeded`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            (1..ALLOWED_ATTACHMENT_COUNT)
                .map { HankeAttachmentFactory.createEntity(hanke = hanke) }
                .let(hankeAttachmentRepository::saveAll)

            assertFailure {
                    hankeAttachmentService.saveAttachment(
                        hanke.hankeTunnus,
                        FILE_NAME_PDF,
                        APPLICATION_PDF_VALUE,
                        blobPath(hanke.id),
                    )
                }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: Attachment limit reached")
                }
        }

        @Test
        fun `Should fail when there is no related hanke`() {
            assertFailure {
                    hankeAttachmentService.saveAttachment(
                        hankeTunnus = "HAI-123",
                        name = FILE_NAME_PDF,
                        type = APPLICATION_PDF_VALUE,
                        blobPath = blobPath(123)
                    )
                }
                .hasClass(HankeNotFoundException::class)

            assertThat(hankeAttachmentRepository.findAll()).isEmpty()
        }

        private fun blobPath(hankeId: Int) = HankeAttachmentContentService.generateBlobPath(hankeId)
    }

    @Nested
    inner class HankeWithRoomForAttachment {
        @Test
        fun `When there is still room, should return hanke info`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()

            val result = hankeAttachmentService.hankeWithRoomForAttachment(hanke.hankeTunnus)

            assertThat(result).all {
                prop(HankeIdentifier::id).isEqualTo(hanke.id)
                prop(HankeIdentifier::hankeTunnus).isEqualTo(hanke.hankeTunnus)
            }
        }

        @Test
        fun `When allowed attachment amount is exceeded should throw`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            val attachments =
                (1..ALLOWED_ATTACHMENT_COUNT).map {
                    HankeAttachmentFactory.createEntity(hanke = hanke)
                }
            hankeAttachmentRepository.saveAll(attachments)

            assertFailure { hankeAttachmentService.hankeWithRoomForAttachment(hanke.hankeTunnus) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: Attachment limit reached")
                }
        }
    }

    @Nested
    inner class DeleteAttachment {
        @Test
        fun `throws exception when attachment is missing`() {
            val attachmentId = UUID.fromString("ab7993b7-a775-4eac-b5b7-8546332944fe")

            val failure = assertFailure { hankeAttachmentService.deleteAttachment(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Nested
        @ExtendWith(MockFileClientExtension::class)
        inner class FromCloud {
            private val path = "in/cloud"

            @Test
            fun `deletes attachment and content when attachment exists`() {
                val attachment = hankeAttachmentFactory.save().withCloudContent(path).value
                assertThat(hankeAttachmentRepository.findAll()).hasSize(1)
                assertThat(fileClient.listBlobs(HANKE_LIITTEET)).hasSize(1)

                hankeAttachmentService.deleteAttachment(attachment.id!!)

                assertThat(hankeAttachmentRepository.findAll()).isEmpty()
                assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
            }
        }
    }

    @Nested
    @ExtendWith(MockFileClientExtension::class)
    inner class DeleteAllAttachments {
        @Test
        fun `does not throw exception when hanke does not exist`() {
            hankeAttachmentService.deleteAllAttachments(HankeIdentifierFactory.create())
        }

        @Test
        fun `does not throw exception when hanke has no attachments`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()

            hankeAttachmentService.deleteAllAttachments(hanke)
        }

        @Test
        fun `deletes all attachments and their contents from hanke`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            hankeAttachmentFactory.save(hanke = hanke).withCloudContent()
            hankeAttachmentFactory.save(hanke = hanke).withCloudContent()
            assertThat(hankeAttachmentRepository.findAll()).hasSize(2)
            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).hasSize(2)

            hankeAttachmentService.deleteAllAttachments(hanke)

            assertThat(hankeAttachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `deletes attachments only from the specified hanke`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            hankeAttachmentFactory.save(hanke = hanke).withCloudContent()
            val otherAttachment = hankeAttachmentFactory.save().withCloudContent().value

            hankeAttachmentService.deleteAllAttachments(hanke)

            assertThat(hankeAttachmentRepository.findByIdOrNull(otherAttachment.id)).isNotNull()
            assertThat(fileClient.listBlobs(HANKE_LIITTEET).map { it.path })
                .containsExactly(otherAttachment.blobLocation)
        }
    }
}
