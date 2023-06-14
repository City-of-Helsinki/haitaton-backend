package fi.hel.haitaton.hanke.attachment.hanke

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isPresent
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
@TestPropertySource(locations = ["classpath:application-test.properties"])
class HankeAttachmentServiceITests : DatabaseTest() {
    @Autowired private lateinit var hankeAttachmentService: HankeAttachmentService
    @Autowired private lateinit var hankeAttachmentRepository: HankeAttachmentRepository
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var hankeFactory: HankeFactory

    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start(6789)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getMetadataList should return related metadata list`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        mockWebServer.enqueue(response(body(results = successResult())))
        val hanke = hankeService.createHanke(HankeFactory.create())
        (1..2).forEach { _ ->
            hankeAttachmentService.addAttachment(
                hankeTunnus = hanke.hankeTunnus!!,
                attachment = testFile()
            )
        }

        val result = hankeAttachmentService.getMetadataList(hanke.hankeTunnus!!)

        assertThat(result).hasSize(2)
        assertThat(result).each { d ->
            d.transform { it.id }.isNotNull()
            d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
            d.transform { it.createdByUserId }.isEqualTo(USERNAME)
            d.transform { it.createdAt }.isNotNull()
            d.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus)
        }
    }

    @Test
    fun `getContent when status is OK should succeed`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val file = testFile()
        val hanke = hankeService.createHanke(HankeFactory.create())
        val attachment = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, file)

        val result = hankeAttachmentService.getContent(hanke.hankeTunnus!!, attachment.id!!)

        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
        assertThat(result.bytes).isEqualTo(file.bytes)
    }

    @Test
    fun `getContent when attachment is not in requested hanke should throw`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        mockWebServer.enqueue(response(body(results = successResult())))
        val firstHanke = hankeService.createHanke(HankeFactory.create())
        val secondHanke = hankeService.createHanke(HankeFactory.create())
        hankeAttachmentService.addAttachment(
            hankeTunnus = firstHanke.hankeTunnus!!,
            attachment = testFile(),
        )
        val secondAttachment =
            hankeAttachmentService.addAttachment(
                hankeTunnus = secondHanke.hankeTunnus!!,
                attachment = testFile(),
            )

        val exception =
            assertThrows<AttachmentNotFoundException> {
                hankeAttachmentService.getContent(firstHanke.hankeTunnus!!, secondAttachment.id!!)
            }

        assertThat(exception.message).isEqualTo("Attachment not found, id=${secondAttachment.id}")
    }

    @Test
    fun `addAttachment when valid input returns metadata of saved attachment`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val hanke = hankeService.createHanke(HankeFactory.create())

        val result = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())

        assertThat(result.id).isNotNull()
        assertThat(result.createdByUserId).isEqualTo(USERNAME)
        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.createdAt).isNotNull()
        assertThat(result.hankeTunnus).isEqualTo(hanke.hankeTunnus)
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
                    hankeTunnus = hanke.hankeTunnus!!,
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
        val hanke = hankeService.createHanke(HankeFactory.create())
        val invalidFilename = "hello.html"

        val ex =
            assertThrows<AttachmentInvalidException> {
                hankeAttachmentService.addAttachment(
                    hanke.hankeTunnus!!,
                    testFile(fileName = invalidFilename),
                )
            }

        assertThat(ex.message)
            .isEqualTo("Attachment upload exception: File 'hello.html' not supported")
        assertThat(hankeAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when scan fails should throw`() {
        mockWebServer.enqueue(response(body(results = failResult())))
        val hanke = hankeService.createHanke(HankeFactory.create())

        val exception =
            assertThrows<AttachmentInvalidException> {
                hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())
            }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: Infected file detected, see previous logs.")
        assertThat(hankeAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `deleteAttachment when valid input should succeed`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val hanke = hankeService.createHanke(HankeFactory.create())
        val attachment = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())
        val attachmentId = attachment.id!!
        assertThat(hankeAttachmentRepository.findById(attachmentId)).isPresent()

        hankeAttachmentService.deleteAttachment(hanke.hankeTunnus!!, attachmentId)

        val remainingAttachment = hankeAttachmentRepository.findById(attachmentId)
        assertThat(remainingAttachment).isEmpty()
    }
}
