package fi.hel.haitaton.hanke.attachment.application

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.HANKE_TUNNUS
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.LIIKENNEJARJESTELY
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.VALTAKIRJA
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import java.util.Optional
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.MediaType.TEXT_HTML_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
@TestPropertySource(locations = ["classpath:application-test.properties"])
class ApplicationAttachmentServiceITest : DatabaseTest() {
    @Autowired private lateinit var applicationAttachmentService: ApplicationAttachmentService
    @Autowired private lateinit var applicationAttachmentRepository: ApplicationAttachmentRepository
    @Autowired private lateinit var alluDataFactory: AlluDataFactory
    @Autowired private lateinit var hankeRepository: HankeRepository

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
        val application = initApplication()
        (1..2).forEach { _ ->
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = MUU,
                attachment = testFile()
            )
        }

        val result = applicationAttachmentService.getMetadataList(application.id!!)

        assertThat(result).hasSize(2)
        assertThat(result).each { d ->
            d.transform { it.id }.isNotNull()
            d.transform { it.fileName }.endsWith("file.pdf")
            d.transform { it.createdByUserId }.isEqualTo(USERNAME)
            d.transform { it.createdAt }.isRecent()
            d.transform { it.scanStatus }.isEqualTo(OK)
            d.transform { it.applicationId }.isEqualTo(application.id)
            d.transform { it.attachmentType }.isEqualTo(MUU)
        }
    }

    @Test
    fun `getContent when status is OK should succeed`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication()
        val file = testFile()
        val attachment =
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = VALTAKIRJA,
                attachment = file
            )

        val result =
            applicationAttachmentService.getContent(
                applicationId = application.id!!,
                attachmentId = attachment.id!!
            )

        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
        assertThat(result.bytes).isEqualTo(file.bytes)
    }

    @Test
    fun `getContent when attachment is not in requested application should throw`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        mockWebServer.enqueue(response(body(results = successResult())))
        val firstApplication = initApplication()
        val secondApplication = initApplication()
        applicationAttachmentService.addAttachment(
            applicationId = firstApplication.id!!,
            attachmentType = VALTAKIRJA,
            attachment = testFile(),
        )
        val secondAttachment =
            applicationAttachmentService.addAttachment(
                applicationId = secondApplication.id!!,
                attachmentType = LIIKENNEJARJESTELY,
                attachment = testFile(),
            )

        val exception =
            assertThrows<AttachmentNotFoundException> {
                applicationAttachmentService.getContent(
                    applicationId = firstApplication.id!!,
                    attachmentId = secondAttachment.id!!,
                )
            }

        assertThat(exception.message).isEqualTo("Attachment ${secondAttachment.id} not found")
    }

    @EnumSource(ApplicationAttachmentType::class)
    @ParameterizedTest
    fun `addAttachment when valid data should succeed`(typeInput: ApplicationAttachmentType) {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application =
            alluDataFactory.saveApplicationEntity(username = USERNAME, hanke = hankeEntity())

        val result =
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = typeInput,
                attachment = testFile(),
            )

        assertThat(result.id).isNotNull()
        assertThat(result.createdByUserId).isEqualTo(USERNAME)
        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.createdAt).isRecent()
        assertThat(result.applicationId).isEqualTo(application.id)
        assertThat(result.attachmentType).isEqualTo(typeInput)
        assertThat(result.scanStatus).isEqualTo(OK)
    }

    @Test
    fun `addAttachment when no existing application should throw`() {
        assertThrows<ApplicationNotFoundException> {
            applicationAttachmentService.addAttachment(
                applicationId = 123L,
                attachmentType = MUU,
                attachment = testFile(),
            )
        }

        assertThat(applicationAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when content type does not match file extension should fail`() {
        val application =
            alluDataFactory.saveApplicationEntity(username = USERNAME, hanke = hankeEntity())
        val invalidFilename = "hello.html"

        val exception =
            assertThrows<AttachmentUploadException> {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename)
                )
            }

        assertThat(exception.message)
            .isEqualTo(
                "Attachment upload exception: File '$invalidFilename' extension does not match content type 'application/pdf'"
            )
        assertThat(applicationAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when not supported content type should fail`() {
        val application =
            alluDataFactory.saveApplicationEntity(username = USERNAME, hanke = hankeEntity())

        assertThrows<AttachmentUploadException> {
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = VALTAKIRJA,
                attachment = testFile(contentType = TEXT_HTML_VALUE)
            )
        }

        assertThat(applicationAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when scan fails should throw`() {
        mockWebServer.enqueue(response(body(results = failResult())))
        val application =
            alluDataFactory.saveApplicationEntity(username = USERNAME, hanke = hankeEntity())

        val exception =
            assertThrows<AttachmentUploadException> {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile()
                )
            }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: Infected file detected, see previous logs.")
        assertThat(applicationAttachmentRepository.findAll()).isEmpty()
    }

    @EnumSource(value = AttachmentScanStatus::class, names = ["PENDING", "FAILED"])
    @ParameterizedTest
    fun `getContent when status is not OK should throw`(scanStatus: AttachmentScanStatus) {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication()
        val result =
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = MUU,
                attachment = testFile()
            )

        val attachment = applicationAttachmentRepository.findById(result.id!!).orElseThrow()
        attachment.scanStatus = scanStatus
        applicationAttachmentRepository.save(attachment)

        assertThrows<AttachmentNotFoundException> {
            applicationAttachmentService.getContent(
                applicationId = application.id!!,
                attachmentId = result.id!!
            )
        }
    }

    @Test
    fun `deleteAttachment when valid input should succeed`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication()
        val attachment =
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = VALTAKIRJA,
                attachment = testFile()
            )
        assertThat(applicationAttachmentRepository.findById(attachment.id!!).orElseThrow())
            .isNotNull()

        applicationAttachmentService.deleteAttachment(
            applicationId = application.id!!,
            attachmentId = attachment.id!!
        )

        assertThat(applicationAttachmentRepository.findById(attachment.id!!))
            .isEqualTo(Optional.empty())
    }

    private fun initApplication(): ApplicationEntity =
        alluDataFactory.saveApplicationEntity(username = USERNAME, hanke = hankeEntity())

    private fun hankeEntity(): HankeEntity =
        hankeRepository.save(HankeEntity(hankeTunnus = HANKE_TUNNUS))
}