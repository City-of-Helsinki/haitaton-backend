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
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeIdentifierFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE

class HankeAttachmentServiceITest(
    @Autowired private val hankeAttachmentService: HankeAttachmentService,
    @Autowired private val attachmentRepository: HankeAttachmentRepository,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
    @Autowired private val fileClient: MockFileClient
) : IntegrationTest() {

    private lateinit var mockClamAv: MockWebServer

    @BeforeEach
    fun setup() {
        mockClamAv = MockWebServer()
        mockClamAv.start(6789)
    }

    @AfterEach
    fun tearDown() {
        mockClamAv.shutdown()
    }

    @Nested
    inner class GetMetadataList {
        @Test
        fun `getMetadataList should return related metadata list`() {
            val hanke = hankeFactory.saveMinimal()
            (1..2).forEach { _ ->
                hankeAttachmentFactory.save(
                    fileName = FILE_NAME_PDF,
                    contentType = APPLICATION_PDF_VALUE,
                    size = DEFAULT_SIZE,
                    hanke = hanke,
                )
            }

            val result = hankeAttachmentService.getMetadataList(hanke.hankeTunnus)

            assertThat(result).hasSize(2)
            assertThat(result).each { d ->
                d.transform { it.id }.isNotNull()
                d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
                d.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus)
                d.transform { it.contentType }.isEqualTo(APPLICATION_PDF_VALUE)
                d.transform { it.size }.isEqualTo(DEFAULT_SIZE)
            }
        }
    }

    @Nested
    inner class UploadHankeAttachment {
        @Test
        fun `Should upload blob and return saved metadata`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val hanke = hankeFactory.saveMinimal()
            val file = testFile()

            val result =
                hankeAttachmentService.uploadHankeAttachment(
                    hankeTunnus = hanke.hankeTunnus,
                    attachment = testFile()
                )

            assertThat(result).all {
                prop(HankeAttachmentMetadataDto::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(HankeAttachmentMetadataDto::createdAt).isRecent()
                prop(HankeAttachmentMetadataDto::createdByUserId).isEqualTo(USERNAME)
                prop(HankeAttachmentMetadataDto::fileName).isEqualTo(file.originalFilename)
                prop(HankeAttachmentMetadataDto::contentType).isEqualTo(file.contentType)
                prop(HankeAttachmentMetadataDto::size).isEqualTo(file.size)
            }
            val attachment = attachmentRepository.findById(result.id).orElseThrow()
            val blob = fileClient.download(Container.HANKE_LIITTEET, attachment.blobLocation)
            assertThat(blob.contentType.toString()).isEqualTo(file.contentType)
        }

        @Test
        fun `Should throw when infected file is encountered`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val hanke = hankeFactory.builder(USERNAME).save()

            assertFailure {
                    hankeAttachmentService.uploadHankeAttachment(
                        hankeTunnus = hanke.hankeTunnus,
                        attachment = testFile()
                    )
                }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage(
                        "Attachment upload exception: Infected file detected, see previous logs."
                    )
                }
        }
    }

    @Nested
    inner class GetContent {

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
            val path = "in/cloud"
            val attachment = hankeAttachmentFactory.save().withContent(path).value

            val result = hankeAttachmentService.getContent(attachment.id!!)

            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
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

        @Test
        fun `deletes attachment and content when attachment exists`() {
            val path = "in/cloud"
            val attachment = hankeAttachmentFactory.save().withContent(path).value
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).hasSize(1)

            hankeAttachmentService.deleteAttachment(attachment.id!!)

            assertThat(attachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).isEmpty()
        }
    }

    @Nested
    inner class DeleteAllAttachments {
        @Test
        fun `does not throw exception when hanke does not exist`() {
            hankeAttachmentService.deleteAllAttachments(HankeIdentifierFactory.create())
        }

        @Test
        fun `does not throw exception when hanke has no attachments`() {
            val hanke = hankeFactory.saveMinimal()

            hankeAttachmentService.deleteAllAttachments(hanke)
        }

        @Test
        fun `deletes all attachments and their contents from hanke`() {
            val hanke = hankeFactory.saveMinimal()
            hankeAttachmentFactory.save(hanke = hanke).withContent()
            hankeAttachmentFactory.save(hanke = hanke).withContent()
            assertThat(attachmentRepository.findAll()).hasSize(2)
            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).hasSize(2)

            hankeAttachmentService.deleteAllAttachments(hanke)

            assertThat(attachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET)).isEmpty()
        }

        @Test
        fun `deletes attachments only from the specified hanke`() {
            val hanke = hankeFactory.saveMinimal()
            hankeAttachmentFactory.save(hanke = hanke).withContent()
            val otherAttachment = hankeAttachmentFactory.save().withContent().value

            hankeAttachmentService.deleteAllAttachments(hanke)

            assertThat(attachmentRepository.findByIdOrNull(otherAttachment.id)).isNotNull()
            assertThat(fileClient.listBlobs(Container.HANKE_LIITTEET).map { it.path })
                .containsExactly(otherAttachment.blobLocation)
        }
    }
}
