package fi.hel.haitaton.hanke.attachment.application

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isPresent
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.HANDLING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
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
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.defaultData
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createAlluApplicationResponse
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
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
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

private const val ALLU_ID = 42

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
@TestPropertySource(locations = ["classpath:application-test.properties"])
class ApplicationAttachmentServiceITest : DatabaseTest() {
    @MockkBean private lateinit var cableReportService: CableReportService
    @Autowired private lateinit var applicationAttachmentService: ApplicationAttachmentService
    @Autowired private lateinit var attachmentContentService: AttachmentContentService
    @Autowired private lateinit var applicationAttachmentRepository: ApplicationAttachmentRepository
    @Autowired private lateinit var alluDataFactory: AlluDataFactory
    @Autowired private lateinit var attachmentFactory: AttachmentFactory
    @Autowired private lateinit var hankeRepository: HankeRepository

    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockWebServer = MockWebServer()
        mockWebServer.start(6789)
    }

    @AfterEach
    fun tearDown() {
        checkUnnecessaryStub()
        confirmVerified(cableReportService)
        mockWebServer.shutdown()
    }

    @Test
    fun `getMetadataList should return related metadata list`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication().toApplication()
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
            d.transform { it.applicationId }.isEqualTo(application.id)
            d.transform { it.attachmentType }.isEqualTo(MUU)
        }
    }

    @Test
    fun `getContent when status is OK should succeed`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication().toApplication()
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
                attachmentId = attachment.id
            )

        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
        assertThat(result.bytes).isEqualTo(file.bytes)
    }

    @Test
    fun `getContent when attachment is not in requested application should throw`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        mockWebServer.enqueue(response(body(results = successResult())))
        val firstApplication = initApplication().toApplication()
        val secondApplication = initApplication().toApplication()
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
                    attachmentId = secondAttachment.id,
                )
            }

        assertThat(exception.message).isEqualTo("Attachment not found, id=${secondAttachment.id}")
    }

    @EnumSource(ApplicationAttachmentType::class)
    @ParameterizedTest
    fun `addAttachment when valid data should succeed`(typeInput: ApplicationAttachmentType) {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication().toApplication()

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

        val attachments = applicationAttachmentRepository.findAll()
        assertThat(attachments).hasSize(1)
        val attachmentInDb = attachments.first()
        assertThat(attachmentInDb.id).isEqualTo(result.id)
        assertThat(attachmentInDb.createdByUserId).isEqualTo(USERNAME)
        assertThat(attachmentInDb.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(attachmentInDb.createdAt).isRecent()
        assertThat(attachmentInDb.applicationId).isEqualTo(application.id)
        assertThat(attachmentInDb.attachmentType).isEqualTo(typeInput)

        val content = attachmentContentService.findApplicationContent(result.id)
        assertThat(content).containsExactly(*defaultData)

        verify { cableReportService wasNot Called }
    }

    @Test
    fun `addAttachment with special characters in filename sanitizes filename`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication().toApplication()

        val result =
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = MUU,
                attachment = testFile(fileName = "exa*mple.pdf"),
            )

        assertThat(result.fileName).isEqualTo("exa_mple.pdf")
        val attachmentInDb = applicationAttachmentRepository.getReferenceById(result.id)
        assertThat(attachmentInDb.fileName).isEqualTo("exa_mple.pdf")
    }

    @Test
    fun `addAttachment when allowed attachment amount is reached should throw`() {
        val application = initApplication()
        val attachments =
            (1..ALLOWED_ATTACHMENT_COUNT).map {
                AttachmentFactory.applicationAttachmentEntity(applicationId = application.id!!)
            }
        applicationAttachmentRepository.saveAll(attachments)

        val exception =
            assertThrows<AttachmentLimitReachedException> {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile()
                )
            }

        assertThat(exception.message)
            .isEqualTo(
                "Attachment amount limit reached, limit=$ALLOWED_ATTACHMENT_COUNT, applicationId=${application.id}"
            )
    }

    @Test
    fun `addAttachment without content type should throw`() {
        val application = initApplication()

        val exception =
            assertThrows<AttachmentInvalidException> {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(contentType = null)
                )
            }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: Content-type was not set")
    }

    @Test
    fun `addAttachment when allu handling has started should throw`() {
        val application =
            alluDataFactory
                .saveApplicationEntity(username = USERNAME, hanke = hankeEntity()) {
                    it.alluid = ALLU_ID
                }
                .toApplication()
        every { cableReportService.getApplicationInformation(ALLU_ID) } returns
            createAlluApplicationResponse(status = HANDLING)

        val exception =
            assertThrows<ApplicationAlreadyProcessingException> {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = MUU,
                    attachment = testFile(),
                )
            }

        assertThat(exception.message)
            .isEqualTo(
                "Application is no longer pending in Allu, id=${application.id!!}, alluid=${application.alluid!!}"
            )
        verify { cableReportService.getApplicationInformation(ALLU_ID) }
    }

    @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
    @ParameterizedTest
    fun `addAttachment when application pending should send also`(status: ApplicationStatus) {
        justRun { cableReportService.addAttachment(ALLU_ID, any()) }
        mockWebServer.enqueue(response(body(results = successResult())))
        val application =
            alluDataFactory
                .saveApplicationEntity(username = USERNAME, hanke = hankeEntity()) {
                    it.alluid = ALLU_ID
                    it.alluStatus = PENDING
                }
                .toApplication()
        every { cableReportService.getApplicationInformation(ALLU_ID) } returns
            createAlluApplicationResponse(status = status)

        applicationAttachmentService.addAttachment(
            applicationId = application.id!!,
            attachmentType = LIIKENNEJARJESTELY,
            attachment = testFile(),
        )

        verifyOrder {
            cableReportService.getApplicationInformation(ALLU_ID)
            cableReportService.addAttachment(ALLU_ID, any())
        }
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
    fun `addAttachment when type not supported should fail`() {
        val application = initApplication().toApplication()
        val invalidFilename = "hello.html"

        val exception =
            assertThrows<AttachmentInvalidException> {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename)
                )
            }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: File 'hello.html' not supported")
        assertThat(applicationAttachmentRepository.findAll()).isEmpty()
    }

    @Test
    fun `addAttachment when scan fails should throw`() {
        mockWebServer.enqueue(response(body(results = failResult())))
        val application = initApplication().toApplication()

        val exception =
            assertThrows<AttachmentInvalidException> {
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

    @Test
    fun `deleteAttachment when valid input should succeed`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication().toApplication()
        val attachment =
            applicationAttachmentService.addAttachment(
                applicationId = application.id!!,
                attachmentType = VALTAKIRJA,
                attachment = testFile()
            )
        assertThat(applicationAttachmentRepository.findById(attachment.id)).isPresent()

        applicationAttachmentService.deleteAttachment(
            applicationId = application.id!!,
            attachmentId = attachment.id
        )

        assertThat(applicationAttachmentRepository.findById(attachment.id)).isEmpty()
    }

    @Test
    fun `deleteAttachment when application has been sent to Allu should throw`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = initApplication { it.alluid = ALLU_ID }
        val attachment = attachmentFactory.saveAttachment(application.id!!)

        val exception =
            assertThrows<ApplicationInAlluException> {
                applicationAttachmentService.deleteAttachment(
                    applicationId = application.id!!,
                    attachmentId = attachment.id!!
                )
            }

        assertThat(exception.message)
            .isEqualTo(
                "Application is already sent to Allu, " +
                    "applicationId=${application.id}, alluId=${application.alluid}"
            )
        assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isPresent()
        verify { cableReportService wasNot Called }
    }

    private fun initApplication(mutator: (ApplicationEntity) -> Unit = {}): ApplicationEntity =
        alluDataFactory.saveApplicationEntity(
            username = USERNAME,
            hanke = hankeEntity(),
            mutator = mutator
        )

    private fun hankeEntity(): HankeEntity =
        hankeRepository.save(HankeEntity(hankeTunnus = HANKE_TUNNUS))
}
