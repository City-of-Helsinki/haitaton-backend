package fi.hel.haitaton.hanke.attachment.application

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
import fi.hel.haitaton.hanke.allu.AlluException
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.HANDLING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.LIIKENNEJARJESTELY
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.VALTAKIRJA
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.Asserts.isSameInstantAs
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.UUID
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val ALLU_ID = 42

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ApplicationAttachmentServiceITest : DatabaseTest() {
    @Autowired private lateinit var cableReportService: CableReportService
    @Autowired private lateinit var applicationAttachmentService: ApplicationAttachmentService
    @Autowired private lateinit var attachmentContentService: ApplicationAttachmentContentService
    @Autowired private lateinit var applicationAttachmentRepository: ApplicationAttachmentRepository
    @Autowired private lateinit var contentRepository: ApplicationAttachmentContentRepository
    @Autowired private lateinit var alluDataFactory: AlluDataFactory
    @Autowired private lateinit var attachmentFactory: ApplicationAttachmentFactory

    private lateinit var mockWebServer: MockWebServer

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockWebServer = MockWebServer()
        mockWebServer.start(6789)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        checkUnnecessaryStub()
        confirmVerified(cableReportService)
    }

    @Test
    fun `getMetadataList should return related metadata list`() {
        val application = alluDataFactory.saveApplicationEntity(USERNAME)
        attachmentFactory.save(application = application).withDbContent()
        attachmentFactory.save(application = application).withDbContent()

        val result = applicationAttachmentService.getMetadataList(application.id!!)

        assertThat(result).hasSize(2)
        assertThat(result).each { d ->
            d.transform { it.id }.isNotNull()
            d.transform { it.fileName }.endsWith("file.pdf")
            d.transform { it.createdByUserId }.isEqualTo(USERNAME)
            d.transform { it.createdAt }.isSameInstantAs(ApplicationAttachmentFactory.CREATED_AT)
            d.transform { it.applicationId }.isEqualTo(application.id)
            d.transform { it.attachmentType }.isEqualTo(MUU)
        }
    }

    @Nested
    inner class GetContent {
        @Test
        fun `throws exception when attachment not found`() {
            val attachmentId = UUID.fromString("93b5c49d-918a-453d-a2bf-b918b47923c1")

            val failure = assertFailure { applicationAttachmentService.getContent(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Nested
        inner class FromDb {
            @Test
            fun `returns the attachment content, filename and type`() {
                val attachment = attachmentFactory.save().withDbContent().value

                val result = applicationAttachmentService.getContent(attachmentId = attachment.id!!)

                assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(result.contentType).isEqualTo(MediaType.APPLICATION_PDF_VALUE)
                assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
            }
        }

        @Nested
        @ExtendWith(MockFileClientExtension::class)
        inner class FromCloud {
            @Test
            fun `returns the attachment content, filename and type`() {
                val attachment = attachmentFactory.save().withCloudContent().value

                val result = applicationAttachmentService.getContent(attachmentId = attachment.id!!)

                assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(result.contentType).isEqualTo(MediaType.APPLICATION_PDF_VALUE)
                assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
            }
        }
    }

    @EnumSource(ApplicationAttachmentType::class)
    @ParameterizedTest
    fun `addAttachment when valid data should succeed`(typeInput: ApplicationAttachmentType) {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = alluDataFactory.saveApplicationEntity(USERNAME)

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

        val content = attachmentContentService.find(attachmentInDb.toDomain())
        assertThat(content).isEqualTo(DEFAULT_DATA)

        verify { cableReportService wasNot Called }
    }

    @Test
    fun `addAttachment with special characters in filename sanitizes filename`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application = alluDataFactory.saveApplicationEntity(USERNAME)

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
        val application = alluDataFactory.saveApplicationEntity(USERNAME)
        val attachments =
            (1..ALLOWED_ATTACHMENT_COUNT).map {
                ApplicationAttachmentFactory.createEntity(applicationId = application.id!!)
            }
        applicationAttachmentRepository.saveAll(attachments)
        mockWebServer.enqueue(response(body(results = successResult())))

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
        val application = alluDataFactory.saveApplicationEntity(USERNAME)

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
                .saveApplicationEntity(username = USERNAME) { it.alluid = ALLU_ID }
                .toApplication()
        every { cableReportService.getApplicationInformation(ALLU_ID) } returns
            AlluDataFactory.createAlluApplicationResponse(status = HANDLING)

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
                .saveApplicationEntity(username = USERNAME) {
                    it.alluid = ALLU_ID
                    it.alluStatus = PENDING
                }
                .toApplication()
        every { cableReportService.getApplicationInformation(ALLU_ID) } returns
            AlluDataFactory.createAlluApplicationResponse(status = status)

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
        val application = alluDataFactory.saveApplicationEntity(USERNAME)
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
    fun `addAttachment when Allu upload fails should clean DB and throw`() {
        mockWebServer.enqueue(response(body(results = successResult())))
        val application =
            alluDataFactory
                .saveApplicationEntity(username = USERNAME) {
                    it.alluid = ALLU_ID
                    it.alluStatus = PENDING
                }
                .toApplication()
        every { cableReportService.getApplicationInformation(ALLU_ID) } returns
            AlluDataFactory.createAlluApplicationResponse(status = PENDING)
        every { cableReportService.addAttachment(ALLU_ID, any()) } throws AlluException(listOf())

        assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = LIIKENNEJARJESTELY,
                    attachment = testFile(),
                )
            }
            .hasClass(AlluException::class)

        assertThat(applicationAttachmentRepository.findAll()).isEmpty()
        assertThat(contentRepository.findAll()).isEmpty()
        verifyOrder {
            cableReportService.getApplicationInformation(ALLU_ID)
            cableReportService.addAttachment(ALLU_ID, any())
        }
    }

    @Test
    fun `addAttachment when scan fails should throw`() {
        mockWebServer.enqueue(response(body(results = failResult())))
        val application = alluDataFactory.saveApplicationEntity(USERNAME)

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
        val attachment = attachmentFactory.save().withDbContent().value
        assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isPresent()

        applicationAttachmentService.deleteAttachment(attachmentId = attachment.id!!)

        assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isEmpty()
    }

    @Test
    fun `deleteAttachment when application has been sent to Allu should throw`() {
        val application = alluDataFactory.saveApplicationEntity(USERNAME) { it.alluid = ALLU_ID }
        val attachment = attachmentFactory.save(application = application).withDbContent().value

        val exception =
            assertThrows<ApplicationInAlluException> {
                applicationAttachmentService.deleteAttachment(attachmentId = attachment.id!!)
            }

        assertThat(exception.message)
            .isEqualTo(
                "Application is already sent to Allu, " +
                    "applicationId=${application.id}, alluId=${application.alluid}"
            )
        assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isPresent()
        verify { cableReportService wasNot Called }
    }
}
