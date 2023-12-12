package fi.hel.haitaton.hanke.attachment.application

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isPresent
import assertk.assertions.messageContains
import assertk.assertions.prop
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
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val ALLU_ID = 42

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ApplicationAttachmentServiceITest(
    @Autowired private val cableReportService: CableReportService,
    @Autowired private val applicationAttachmentService: ApplicationAttachmentService,
    @Autowired private val applicationAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val contentRepository: ApplicationAttachmentContentRepository,
    @Autowired private val alluDataFactory: AlluDataFactory,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
) : DatabaseTest() {
    private lateinit var mockClamAv: MockWebServer

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockClamAv = MockWebServer()
        mockClamAv.start(6789)
    }

    @AfterEach
    fun tearDown() {
        mockClamAv.shutdown()
        checkUnnecessaryStub()
        confirmVerified(cableReportService)
    }

    @Nested
    inner class GetMetadataList {
        @Test
        fun `returns empty when application doesn't exist`() {
            val applicationId = 698432169874L

            val result = applicationAttachmentService.getMetadataList(applicationId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `return empty when application doesn't have attachments`() {
            val application = alluDataFactory.saveApplicationEntity(USERNAME)

            val result = applicationAttachmentService.getMetadataList(application.id!!)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns related metadata list`() {
            val application = alluDataFactory.saveApplicationEntity(USERNAME)
            attachmentFactory.save(application = application).withDbContent()
            attachmentFactory.save(application = application).withDbContent()

            val result = applicationAttachmentService.getMetadataList(application.id!!)

            assertThat(result).hasSize(2)
            assertThat(result).each {
                it.prop(ApplicationAttachmentMetadataDto::id).isNotNull()
                it.prop(ApplicationAttachmentMetadataDto::fileName).endsWith("file.pdf")
                it.prop(ApplicationAttachmentMetadataDto::createdByUserId).isEqualTo(USERNAME)
                it.prop(ApplicationAttachmentMetadataDto::createdAt)
                    .isSameInstantAs(ApplicationAttachmentFactory.CREATED_AT)
                it.prop(ApplicationAttachmentMetadataDto::applicationId).isEqualTo(application.id)
                it.prop(ApplicationAttachmentMetadataDto::attachmentType).isEqualTo(MUU)
            }
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

    @Nested
    inner class AddAttachment {
        @EnumSource(ApplicationAttachmentType::class)
        @ParameterizedTest
        fun `saves attachment and content to DB`(typeInput: ApplicationAttachmentType) {
            mockClamAv.enqueue(response(body(results = successResult())))
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

            val content = contentRepository.findByIdOrNull(attachmentInDb.id!!)
            assertThat(content)
                .isNotNull()
                .prop(ApplicationAttachmentContentEntity::content)
                .isEqualTo(DEFAULT_DATA)

            verify { cableReportService wasNot Called }
        }

        @Test
        fun `sanitizes filenames with special characters`() {
            mockClamAv.enqueue(response(body(results = successResult())))
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
        fun `throws exception when allowed attachment amount is reached`() {
            val application = alluDataFactory.saveApplicationEntity(USERNAME)
            val attachments =
                (1..ALLOWED_ATTACHMENT_COUNT).map {
                    ApplicationAttachmentFactory.createEntity(applicationId = application.id!!)
                }
            applicationAttachmentRepository.saveAll(attachments)
            mockClamAv.enqueue(response(body(results = successResult())))

            val failure = assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile()
                )
            }

            failure.all {
                hasClass(AttachmentLimitReachedException::class)
                hasMessage(
                    "Attachment amount limit reached, limit=$ALLOWED_ATTACHMENT_COUNT, applicationId=${application.id}"
                )
            }
        }

        @Test
        fun `throws exception without content type`() {
            val application = alluDataFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(contentType = null)
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage("Attachment upload exception: Content-type was not set")
            }
        }

        @Test
        fun `throws exception when allu handling has started`() {
            val application =
                alluDataFactory.saveApplicationEntity(username = USERNAME, alluId = ALLU_ID)
            every { cableReportService.getApplicationInformation(ALLU_ID) } returns
                AlluDataFactory.createAlluApplicationResponse(status = HANDLING)

            val failure = assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = MUU,
                    attachment = testFile(),
                )
            }

            failure.all {
                hasClass(ApplicationAlreadyProcessingException::class)
                hasMessage(
                    "Application is no longer pending in Allu, id=${application.id!!}, alluid=${application.alluid!!}"
                )
            }
            verify { cableReportService.getApplicationInformation(ALLU_ID) }
        }

        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        @ParameterizedTest
        fun `sends attachment to Allu when application is pending`(status: ApplicationStatus) {
            justRun { cableReportService.addAttachment(ALLU_ID, any()) }
            mockClamAv.enqueue(response(body(results = successResult())))
            val application =
                alluDataFactory.saveApplicationEntity(
                    username = USERNAME,
                    alluId = ALLU_ID,
                    alluStatus = PENDING
                )
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
        fun `throws exception when there is no existing application`() {
            val failure = assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = 123L,
                    attachmentType = MUU,
                    attachment = testFile(),
                )
            }

            failure.hasClass(ApplicationNotFoundException::class)
            assertThat(applicationAttachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `throws exception when file type is not supported`() {
            val application = alluDataFactory.saveApplicationEntity(USERNAME)
            val invalidFilename = "hello.html"

            val failure = assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename)
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage("Attachment upload exception: File 'hello.html' not supported")
            }
            assertThat(applicationAttachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `clean DB and throws exception when Allu upload fails`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val application =
                alluDataFactory.saveApplicationEntity(
                    username = USERNAME,
                    alluId = ALLU_ID,
                    alluStatus = PENDING
                )
            every { cableReportService.getApplicationInformation(ALLU_ID) } returns
                AlluDataFactory.createAlluApplicationResponse(status = PENDING)
            every { cableReportService.addAttachment(ALLU_ID, any()) } throws
                AlluException(listOf())

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
        fun `throws exception when virus scan fails`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val application = alluDataFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                applicationAttachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile()
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage(
                    "Attachment upload exception: Infected file detected, see previous logs."
                )
            }
            assertThat(applicationAttachmentRepository.findAll()).isEmpty()
        }
    }

    @Nested
    inner class DeleteAttachment {
        @Test
        fun `deletes attachment from DB`() {
            val attachment = attachmentFactory.save().withDbContent().value
            assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isPresent()

            applicationAttachmentService.deleteAttachment(attachmentId = attachment.id!!)

            assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isEmpty()
        }

        @Test
        fun `throws exception when application has been sent to Allu`() {
            val application = alluDataFactory.saveApplicationEntity(USERNAME, alluId = ALLU_ID)
            val attachment = attachmentFactory.save(application = application).withDbContent().value

            val failure = assertFailure {
                applicationAttachmentService.deleteAttachment(attachmentId = attachment.id!!)
            }

            failure.all {
                hasClass(ApplicationInAlluException::class)
                hasMessage(
                    "Application is already sent to Allu, " +
                        "applicationId=${application.id}, alluId=${application.alluid}"
                )
            }
            assertThat(applicationAttachmentRepository.findById(attachment.id!!)).isPresent()
            verify { cableReportService wasNot Called }
        }
    }
}
