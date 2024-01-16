package fi.hel.haitaton.hanke.attachment.application

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
import assertk.assertions.isNull
import assertk.assertions.isPresent
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.startsWith
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
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container.HAKEMUS_LIITTEET
import fi.hel.haitaton.hanke.attachment.body
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
import fi.hel.haitaton.hanke.attachment.common.DownloadResponse
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
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
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val ALLU_ID = 42

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ApplicationAttachmentServiceITest(
    @Autowired private val cableReportService: CableReportService,
    @Autowired private val attachmentService: ApplicationAttachmentService,
    @Autowired private val attachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val contentRepository: ApplicationAttachmentContentRepository,
    @Autowired private val applicationFactory: ApplicationFactory,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val fileClient: MockFileClient,
) : DatabaseTest() {
    private lateinit var mockClamAv: MockWebServer

    @BeforeEach
    fun setup() {
        clearAllMocks()
        mockClamAv = MockWebServer()
        mockClamAv.start(6789)
        fileClient.recreateContainers()
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
        fun `Returns empty when application doesn't exist`() {
            val applicationId = 698432169874L

            val result = attachmentService.getMetadataList(applicationId)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns empty when application doesn't have attachments`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val result = attachmentService.getMetadataList(application.id!!)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns related metadata list`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)
            attachmentFactory.save(application = application).withDbContent()
            attachmentFactory.save(application = application).withDbContent()

            val result = attachmentService.getMetadataList(application.id!!)

            assertThat(result).hasSize(2)
            assertThat(result).each {
                it.prop(ApplicationAttachmentMetadataDto::id).isNotNull()
                it.prop(ApplicationAttachmentMetadataDto::fileName).endsWith("file.pdf")
                it.prop(ApplicationAttachmentMetadataDto::contentType)
                    .isEqualTo(APPLICATION_PDF_VALUE)
                it.prop(ApplicationAttachmentMetadataDto::size).isEqualTo(DEFAULT_SIZE)
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
        fun `Throws exception when attachment not found`() {
            val attachmentId = UUID.fromString("93b5c49d-918a-453d-a2bf-b918b47923c1")

            val failure = assertFailure { attachmentService.getContent(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Nested
        inner class FromDb {
            @Test
            fun `Returns the attachment content, filename and type`() {
                val attachment = attachmentFactory.save().withDbContent().value

                val result = attachmentService.getContent(attachmentId = attachment.id!!)

                assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(result.contentType).isEqualTo(MediaType.APPLICATION_PDF_VALUE)
                assertThat(result.bytes).isEqualTo(DEFAULT_DATA)
            }
        }

        @Nested
        @ExtendWith(MockFileClientExtension::class)
        inner class FromCloud {
            @Test
            fun `Returns the attachment content, filename and type`() {
                val attachment = attachmentFactory.save().withCloudContent().value

                val result = attachmentService.getContent(attachmentId = attachment.id!!)

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
        fun `Saves attachment metadata to DB and content to blob storage`(
            typeInput: ApplicationAttachmentType
        ) {
            mockClamAv.enqueue(response(body(results = successResult())))
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val result =
                attachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = typeInput,
                    attachment = testFile(),
                )

            assertThat(result.id).isNotNull()
            assertThat(result.createdByUserId).isEqualTo(USERNAME)
            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(MediaType.APPLICATION_PDF_VALUE)
            assertThat(result.size).isEqualTo(DEFAULT_SIZE)
            assertThat(result.createdAt).isRecent()
            assertThat(result.applicationId).isEqualTo(application.id)
            assertThat(result.attachmentType).isEqualTo(typeInput)

            val attachments = attachmentRepository.findAll()
            assertThat(attachments).hasSize(1)
            val attachmentInDb = attachments.first()
            assertThat(attachmentInDb.id).isEqualTo(result.id)
            assertThat(attachmentInDb.createdByUserId).isEqualTo(USERNAME)
            assertThat(attachmentInDb.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(attachmentInDb.contentType).isEqualTo(MediaType.APPLICATION_PDF_VALUE)
            assertThat(attachmentInDb.size).isEqualTo(DEFAULT_SIZE)
            assertThat(attachmentInDb.createdAt).isRecent()
            assertThat(attachmentInDb.applicationId).isEqualTo(application.id)
            assertThat(attachmentInDb.attachmentType).isEqualTo(typeInput)
            assertThat(attachmentInDb.blobLocation).isNotNull().startsWith("${application.id!!}/")

            val content = fileClient.download(HAKEMUS_LIITTEET, attachmentInDb.blobLocation!!)
            assertThat(content)
                .isNotNull()
                .prop(DownloadResponse::content)
                .transform { it.toBytes() }
                .isEqualTo(DEFAULT_DATA)

            verify { cableReportService wasNot Called }
        }

        @Test
        fun `Sanitizes filenames with special characters`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val result =
                attachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = MUU,
                    attachment = testFile(fileName = "exa*mple.pdf"),
                )

            assertThat(result.fileName).isEqualTo("exa_mple.pdf")
            val attachmentInDb = attachmentRepository.getReferenceById(result.id)
            assertThat(attachmentInDb.fileName).isEqualTo("exa_mple.pdf")
        }

        @Test
        fun `Throws exception when allowed attachment amount is reached`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)
            val attachments =
                (1..ALLOWED_ATTACHMENT_COUNT).map {
                    ApplicationAttachmentFactory.createEntity(applicationId = application.id!!)
                }
            attachmentRepository.saveAll(attachments)
            mockClamAv.enqueue(response(body(results = successResult())))

            val failure = assertFailure {
                attachmentService.addAttachment(
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
        fun `Throws exception without content type`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                attachmentService.addAttachment(
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
        fun `Throws exception when allu handling has started`() {
            val application =
                applicationFactory.saveApplicationEntity(username = USERNAME, alluId = ALLU_ID)
            every { cableReportService.getApplicationInformation(ALLU_ID) } returns
                AlluFactory.createAlluApplicationResponse(status = HANDLING)

            val failure = assertFailure {
                attachmentService.addAttachment(
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
        fun `Sends attachment to Allu when application is pending`(status: ApplicationStatus) {
            justRun { cableReportService.addAttachment(ALLU_ID, any()) }
            mockClamAv.enqueue(response(body(results = successResult())))
            val application =
                applicationFactory.saveApplicationEntity(
                    username = USERNAME,
                    alluId = ALLU_ID,
                    alluStatus = PENDING
                )
            every { cableReportService.getApplicationInformation(ALLU_ID) } returns
                AlluFactory.createAlluApplicationResponse(status = status)

            attachmentService.addAttachment(
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
        fun `Throws exception when there is no existing application`() {
            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = 123L,
                    attachmentType = MUU,
                    attachment = testFile(),
                )
            }

            failure.hasClass(ApplicationNotFoundException::class)
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Throws exception when file type is not supported`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)
            val invalidFilename = "hello.html"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id!!,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename)
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage("Attachment upload exception: File 'hello.html' not supported")
            }
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Clean DB and throws exception when Allu upload fails`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val application =
                applicationFactory.saveApplicationEntity(
                    username = USERNAME,
                    alluId = ALLU_ID,
                    alluStatus = PENDING
                )
            every { cableReportService.getApplicationInformation(ALLU_ID) } returns
                AlluFactory.createAlluApplicationResponse(status = PENDING)
            every { cableReportService.addAttachment(ALLU_ID, any()) } throws
                AlluException(listOf())

            assertFailure {
                    attachmentService.addAttachment(
                        applicationId = application.id!!,
                        attachmentType = LIIKENNEJARJESTELY,
                        attachment = testFile(),
                    )
                }
                .hasClass(AlluException::class)

            assertThat(attachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).isEmpty()
            verifyOrder {
                cableReportService.getApplicationInformation(ALLU_ID)
                cableReportService.addAttachment(ALLU_ID, any())
            }
        }

        @Test
        fun `Throws exception when virus scan fails`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                attachmentService.addAttachment(
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
            assertThat(attachmentRepository.findAll()).isEmpty()
        }
    }

    @Nested
    inner class DeleteAttachment {
        @Nested
        inner class DeleteException {
            @Test
            fun `Throws when attachment is missing`() {
                val attachmentId = UUID.fromString("ab7993b7-a775-4eac-b5b7-8546332944fe")

                val failure = assertFailure { attachmentService.deleteAttachment(attachmentId) }

                failure.all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachmentId.toString())
                }
            }

            @Test
            fun `Throws when application has been sent to Allu`() {
                val application =
                    applicationFactory.saveApplicationEntity(USERNAME, alluId = ALLU_ID)
                val attachment =
                    attachmentFactory.save(application = application).withDbContent().value

                val failure = assertFailure {
                    attachmentService.deleteAttachment(attachmentId = attachment.id!!)
                }

                failure.all {
                    hasClass(ApplicationInAlluException::class)
                    hasMessage(
                        "Application is already sent to Allu, " +
                            "applicationId=${application.id}, alluId=${application.alluid}"
                    )
                }
                assertThat(attachmentRepository.findById(attachment.id!!)).isPresent()
                verify { cableReportService wasNot Called }
            }
        }

        @Nested
        inner class FromDb {
            @Test
            fun `Deletes attachment and content when attachment exists`() {
                val attachment = attachmentFactory.save().withDbContent().value
                assertThat(attachmentRepository.findAll()).hasSize(1)
                assertThat(contentRepository.findAll()).hasSize(1)

                attachmentService.deleteAttachment(attachment.id!!)

                assertThat(attachmentRepository.findAll()).isEmpty()
                assertThat(contentRepository.findAll()).isEmpty()
            }
        }

        @Nested
        @ExtendWith(MockFileClientExtension::class)
        inner class FromCloud {
            private val path = "in/cloud"

            @Test
            fun `Deletes attachment and content when attachment exists`() {
                val attachment = attachmentFactory.save().withCloudContent(path).value
                assertThat(attachmentRepository.findAll()).hasSize(1)
                assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET).map { it.path })
                    .containsExactly(attachment.blobLocation)

                attachmentService.deleteAttachment(attachment.id!!)

                assertThat(attachmentRepository.findAll()).isEmpty()
                assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).isEmpty()
            }
        }
    }

    @Nested
    inner class TransferAttachmentToBlobStorage {

        @Test
        fun `does nothing if all content has been transferred`() {
            val attachment = attachmentFactory.save().withCloudContent().value
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).hasSize(1)
            attachmentRepository.findById(attachment.id!!).get().apply {
                assertThat(blobLocation).isNotNull()
                assertThat(blobLocation!!).startsWith("${attachment.applicationId}/")
            }

            attachmentService.transferAttachmentContentToBlobStorage()

            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).hasSize(1)
            attachmentRepository.findById(attachment.id!!).get().apply {
                assertThat(blobLocation).isNotNull()
                assertThat(blobLocation!!).startsWith("${attachment.applicationId}/")
            }
        }

        @Test
        fun `transfers content to blob and updates database`() {
            val attachment = attachmentFactory.save().withDbContent().value
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).hasSize(1)
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).isEmpty()

            attachmentService.transferAttachmentContentToBlobStorage()

            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).hasSize(1)
            attachmentRepository.findById(attachment.id!!).get().apply {
                assertThat(blobLocation).isNotNull()
                assertThat(blobLocation!!).startsWith("${attachment.applicationId}/")
            }
        }

        @Test
        fun `aborts if content is missing in database`() {
            val attachment = attachmentFactory.save().value
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).isEmpty()
            attachmentRepository.findById(attachment.id!!).get().apply {
                assertThat(blobLocation).isNull()
            }

            attachmentService.transferAttachmentContentToBlobStorage()

            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(contentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).isEmpty()
            attachmentRepository.findById(attachment.id!!).get().apply {
                assertThat(blobLocation).isNull()
            }
        }
    }
}
