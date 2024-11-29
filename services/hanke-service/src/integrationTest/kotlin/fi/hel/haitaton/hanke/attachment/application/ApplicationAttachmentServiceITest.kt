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
import assertk.assertions.isPresent
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import assertk.assertions.startsWith
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container.HAKEMUS_LIITTEET
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.VALTAKIRJA
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadResponse
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.Asserts.isSameInstantAs
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.verify
import java.util.UUID
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE

private const val ALLU_ID = 42

class ApplicationAttachmentServiceITest(
    @Autowired private val alluClient: AlluClient,
    @Autowired private val attachmentService: ApplicationAttachmentService,
    @Autowired private val attachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val applicationFactory: ApplicationFactory,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val fileClient: MockFileClient,
) : IntegrationTest() {
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
        confirmVerified(alluClient)
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

            val result = attachmentService.getMetadataList(application.id)

            assertThat(result).isEmpty()
        }

        @Test
        fun `Returns related metadata list`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)
            attachmentFactory.save(application = application).withContent()
            attachmentFactory.save(application = application).withContent()

            val result = attachmentService.getMetadataList(application.id)

            assertThat(result).hasSize(2)
            assertThat(result).each {
                it.prop(ApplicationAttachmentMetadata::id).isNotNull()
                it.prop(ApplicationAttachmentMetadata::fileName).endsWith("file.pdf")
                it.prop(ApplicationAttachmentMetadata::contentType).isEqualTo(APPLICATION_PDF_VALUE)
                it.prop(ApplicationAttachmentMetadata::size).isEqualTo(DEFAULT_SIZE)
                it.prop(ApplicationAttachmentMetadata::createdByUserId).isEqualTo(USERNAME)
                it.prop(ApplicationAttachmentMetadata::createdAt)
                    .isSameInstantAs(ApplicationAttachmentFactory.CREATED_AT)
                it.prop(ApplicationAttachmentMetadata::applicationId).isEqualTo(application.id)
                it.prop(ApplicationAttachmentMetadata::attachmentType).isEqualTo(MUU)
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

        @Test
        fun `Returns the attachment content, filename and type`() {
            val attachment = attachmentFactory.save().withContent().value

            val result = attachmentService.getContent(attachmentId = attachment.id!!)

            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.bytes).isEqualTo(PDF_BYTES)
        }

        @Test
        fun `Throws exception when trying to get valtakirja content`() {
            val attachment = attachmentFactory.save(attachmentType = VALTAKIRJA).withContent().value

            val failure = assertFailure {
                attachmentService.getContent(attachmentId = attachment.id!!)
            }

            failure.all {
                hasClass(ValtakirjaForbiddenException::class)
                messageContains("id=${attachment.id}")
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
                    applicationId = application.id,
                    attachmentType = typeInput,
                    attachment = testFile(),
                )

            assertThat(result.id).isNotNull()
            assertThat(result.createdByUserId).isEqualTo(USERNAME)
            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.size).isEqualTo(DEFAULT_SIZE)
            assertThat(result.createdAt).isRecent()
            assertThat(result.applicationId).isEqualTo(application.id)
            assertThat(result.attachmentType).isEqualTo(typeInput)

            val attachments = attachmentRepository.findAll()
            assertThat(attachments).single().all {
                prop(ApplicationAttachmentEntity::id).isEqualTo(result.id)
                prop(ApplicationAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
                prop(ApplicationAttachmentEntity::fileName).isEqualTo(FILE_NAME_PDF)
                prop(ApplicationAttachmentEntity::contentType).isEqualTo(APPLICATION_PDF_VALUE)
                prop(ApplicationAttachmentEntity::size).isEqualTo(DEFAULT_SIZE)
                prop(ApplicationAttachmentEntity::createdAt).isRecent()
                prop(ApplicationAttachmentEntity::applicationId).isEqualTo(application.id)
                prop(ApplicationAttachmentEntity::attachmentType).isEqualTo(typeInput)
                prop(ApplicationAttachmentEntity::blobLocation)
                    .isNotNull()
                    .startsWith("${application.id}/")
            }

            val content = fileClient.download(HAKEMUS_LIITTEET, attachments.first().blobLocation)
            assertThat(content)
                .isNotNull()
                .prop(DownloadResponse::content)
                .transform { it.toBytes() }
                .isEqualTo(PDF_BYTES)

            verify { alluClient wasNot Called }
        }

        @Test
        fun `Sanitizes filenames with special characters`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val result =
                attachmentService.addAttachment(
                    applicationId = application.id,
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
                    ApplicationAttachmentFactory.createEntity(applicationId = application.id)
                }
            attachmentRepository.saveAll(attachments)
            mockClamAv.enqueue(response(body(results = successResult())))

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(),
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
        fun `Throws exception without content`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(data = byteArrayOf()),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                messageContains("Attachment has no content")
            }
        }

        @Test
        fun `Throws exception without content type`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(contentType = null),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage("Attachment upload exception: Content-Type null")
            }
        }

        @Test
        fun `Throws exception when application already sent to Allu`() {
            val application =
                applicationFactory.saveApplicationEntity(username = USERNAME, alluId = ALLU_ID)

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = MUU,
                    attachment = testFile(),
                )
            }

            failure.all {
                hasClass(ApplicationInAlluException::class)
                hasMessage(
                    "Application is already sent to Allu, applicationId=${application.id}, alluId=${application.alluid}"
                )
            }
            assertThat(attachmentRepository.countByApplicationId(application.id)).isEqualTo(0)
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

            failure.hasClass(HakemusNotFoundException::class)
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Throws exception when file type is not supported`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)
            val invalidFilename = "hello.html"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage("Attachment upload exception: File 'hello.html' not supported")
            }
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Throws exception when file type is not supported for attachment type`() {
            val application = applicationFactory.saveApplicationEntity(USERNAME)
            val invalidFilename = "hello.jpeg"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                messageContains("File extension is not valid for attachment type")
                messageContains("filename=$invalidFilename")
                messageContains("attachmentType=$VALTAKIRJA")
            }
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Throws exception when virus scan fails`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val application = applicationFactory.saveApplicationEntity(USERNAME)

            val failure = assertFailure {
                attachmentService.addAttachment(
                    applicationId = application.id,
                    attachmentType = VALTAKIRJA,
                    attachment = testFile(),
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
                    attachmentFactory.save(application = application).withContent().value

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
                verify { alluClient wasNot Called }
            }

            @Test
            fun `Deletes attachment and content when attachment exists`() {
                val attachment = attachmentFactory.save().withContent().value
                assertThat(attachmentRepository.findAll()).hasSize(1)
                assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET).map { it.path })
                    .containsExactly(attachment.blobLocation)

                attachmentService.deleteAttachment(attachment.id!!)

                assertThat(attachmentRepository.findAll()).isEmpty()
                assertThat(fileClient.listBlobs(HAKEMUS_LIITTEET)).isEmpty()
            }
        }
    }
}
