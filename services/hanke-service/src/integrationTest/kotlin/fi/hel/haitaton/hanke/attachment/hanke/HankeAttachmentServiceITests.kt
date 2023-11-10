package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isPresent
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.util.UUID
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeAttachmentServiceITests : DatabaseTest(), HankeAttachmentFactory {
    @Autowired private lateinit var hankeAttachmentService: HankeAttachmentService
    @Autowired override lateinit var hankeAttachmentRepository: HankeAttachmentRepository
    @Autowired override lateinit var hankeFactory: HankeFactory
    @Autowired override lateinit var fileClient: MockFileClient
    @Autowired
    override lateinit var hankeAttachmentContentRepository: HankeAttachmentContentRepository

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

    @Test
    fun `getMetadataList should return related metadata list`() {
        mockClamAv.enqueue(response(body(results = successResult())))
        mockClamAv.enqueue(response(body(results = successResult())))
        val hanke = hankeFactory.save()
        (1..2).forEach { _ ->
            hankeAttachmentService.addAttachment(
                hankeTunnus = hanke.hankeTunnus,
                attachment = testFile()
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

        @Nested
        inner class FromDb {
            @Test
            fun `returns the attachment content, filename and content type`() {
                val attachment = saveAttachment().withDbContent()

                val result = hankeAttachmentService.getContent(attachment.id!!)

                assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
                assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
            }
        }

        @Nested
        inner class FromCloud {
            private val path = "in/cloud"

            @BeforeEach
            fun clear() {
                fileClient.recreateContainers()
            }

            @Test
            fun `returns the attachment content, filename and content type`() {
                val attachment = saveAttachment().withCloudContent(path)

                val result = hankeAttachmentService.getContent(attachment.id!!)

                assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
                assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
                assertThat(mockClamAv.requestCount).isEqualTo(0)
            }
        }
    }

    @Test
    fun `addAttachment when valid input returns metadata of saved attachment`() {
        mockClamAv.enqueue(response(body(results = successResult())))
        val hanke = hankeFactory.save()

        val result = hankeAttachmentService.addAttachment(hanke.hankeTunnus, testFile())

        assertThat(result.id).isNotNull()
        assertThat(result.createdByUserId).isEqualTo(USERNAME)
        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.createdAt).isNotNull()
        assertThat(result.hankeTunnus).isEqualTo(hanke.hankeTunnus)

        val attachments = hankeAttachmentRepository.findAll()
        assertThat(attachments).hasSize(1)
        val attachmentInDb = attachments.first()
        assertThat(attachmentInDb.createdByUserId).isEqualTo(USERNAME)
        assertThat(attachmentInDb.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(attachmentInDb.createdAt).isNotNull()

        val savedContent = hankeAttachmentContentRepository.getReferenceById(result.id).content
        assertThat(savedContent).isEqualTo(DEFAULT_DATA)
    }

    @Test
    fun `addAttachment with special characters in filename sanitizes filename`() {
        mockClamAv.enqueue(response(body(results = successResult())))
        val hanke = hankeFactory.save()

        val result =
            hankeAttachmentService.addAttachment(
                hanke.hankeTunnus,
                testFile(fileName = "exa*mple.txt")
            )

        assertThat(result.fileName).isEqualTo("exa_mple.txt")
        val attachmentInDb = hankeAttachmentRepository.getReferenceById(result.id)
        assertThat(attachmentInDb.fileName).isEqualTo("exa_mple.txt")
    }

    @Test
    fun `addAttachment when allowed attachment amount is exceeded should throw`() {
        val hanke = hankeFactory.saveEntity()
        val attachments =
            (1..ALLOWED_ATTACHMENT_COUNT).map {
                AttachmentFactory.hankeAttachmentEntity(hanke = hanke)
            }
        hankeAttachmentRepository.saveAll(attachments)

        val exception =
            assertThrows<AttachmentInvalidException> {
                hankeAttachmentService.addAttachment(
                    hankeTunnus = hanke.hankeTunnus,
                    attachment = testFile()
                )
            }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: Attachment amount limit reached")
    }

    @Test
    fun `addAttachment when no related hanke should fail`() {
        assertThrows<HankeNotFoundException> {
            hankeAttachmentService.addAttachment("", testFile())
        }

        assertThat(hankeAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when content type not supported should throw`() {
        val hanke = hankeFactory.save()
        val invalidFilename = "hello.html"

        val ex =
            assertThrows<AttachmentInvalidException> {
                hankeAttachmentService.addAttachment(
                    hanke.hankeTunnus,
                    testFile(fileName = invalidFilename),
                )
            }

        assertThat(ex.message)
            .isEqualTo("Attachment upload exception: File 'hello.html' not supported")
        assertThat(hankeAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when scan fails should throw`() {
        mockClamAv.enqueue(response(body(results = failResult())))
        val hanke = hankeFactory.save()

        val exception =
            assertThrows<AttachmentInvalidException> {
                hankeAttachmentService.addAttachment(hanke.hankeTunnus, testFile())
            }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: Infected file detected, see previous logs.")
        assertThat(hankeAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `deleteAttachment when valid input should succeed`() {
        mockClamAv.enqueue(response(body(results = successResult())))
        val hanke = hankeFactory.save()
        val attachment = hankeAttachmentService.addAttachment(hanke.hankeTunnus, testFile())
        val attachmentId = attachment.id
        assertThat(hankeAttachmentRepository.findById(attachmentId)).isPresent()

        hankeAttachmentService.deleteAttachment(attachmentId)

        val remainingAttachment = hankeAttachmentRepository.findById(attachmentId)
        assertThat(remainingAttachment).isEmpty()
    }

    @Test
    fun `deleteAttachment throws exception when attachment is missing`() {
        val attachmentId = UUID.fromString("ab7993b7-a775-4eac-b5b7-8546332944fe")

        val failure = assertFailure { hankeAttachmentService.deleteAttachment(attachmentId) }

        failure.all {
            hasClass(AttachmentNotFoundException::class)
            messageContains(attachmentId.toString())
        }
    }
}
