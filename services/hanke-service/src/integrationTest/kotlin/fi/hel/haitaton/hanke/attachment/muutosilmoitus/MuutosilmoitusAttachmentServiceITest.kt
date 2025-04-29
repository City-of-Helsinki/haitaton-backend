package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
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
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
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
import fi.hel.haitaton.hanke.factory.MuutosilmoitusAttachmentFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusAlreadySentException
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusNotFoundException
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
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

class MuutosilmoitusAttachmentServiceITest(
    @Autowired private val attachmentService: MuutosilmoitusAttachmentService,
    @Autowired private val attachmentRepository: MuutosilmoitusAttachmentRepository,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val attachmentFactory: MuutosilmoitusAttachmentFactory,
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
    }

    @Nested
    inner class GetContent {
        @Test
        fun `throws exception when attachment not found`() {
            val attachmentId = UUID.fromString("93b5c49d-918a-453d-a2bf-b918b47923c1")

            val failure = assertFailure { attachmentService.getContent(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Test
        fun `throws exception when content not in file storage`() {
            val attachment = attachmentFactory.save()
            fileClient.delete(Container.HAKEMUS_LIITTEET, attachment.blobLocation)

            val failure = assertFailure { attachmentService.getContent(attachment.id!!) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachment.id.toString())
            }
        }

        @Test
        fun `returns the attachment content, filename and type`() {
            val attachment = attachmentFactory.save()

            val result = attachmentService.getContent(attachment.id!!)

            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.bytes).isEqualTo(PDF_BYTES)
        }

        @Test
        fun `throws exception when trying to get valtakirja content`() {
            val attachment =
                attachmentFactory.save(attachmentType = ApplicationAttachmentType.VALTAKIRJA)

            val failure = assertFailure { attachmentService.getContent(attachment.id!!) }

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
        fun `saves attachment metadata to DB and content to blob storage`(
            typeInput: ApplicationAttachmentType
        ) {
            mockClamAv.enqueue(response(body(results = successResult())))
            val muutosilmoitus = muutosilmoitusFactory.builder().save()

            val result =
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = typeInput,
                    attachment = testFile(),
                )

            assertThat(result.id).isNotNull()
            assertThat(result.createdByUserId).isEqualTo(USERNAME)
            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.size).isEqualTo(DEFAULT_SIZE)
            assertThat(result.createdAt).isRecent()
            assertThat(result.muutosilmoitusId).isEqualTo(muutosilmoitus.id)
            assertThat(result.attachmentType).isEqualTo(typeInput)

            val attachments = attachmentRepository.findAll()
            assertThat(attachments).single().all {
                prop(MuutosilmoitusAttachmentEntity::id).isEqualTo(result.id)
                prop(MuutosilmoitusAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
                prop(MuutosilmoitusAttachmentEntity::fileName).isEqualTo(FILE_NAME_PDF)
                prop(MuutosilmoitusAttachmentEntity::contentType).isEqualTo(APPLICATION_PDF_VALUE)
                prop(MuutosilmoitusAttachmentEntity::size).isEqualTo(DEFAULT_SIZE)
                prop(MuutosilmoitusAttachmentEntity::createdAt).isRecent()
                prop(MuutosilmoitusAttachmentEntity::muutosilmoitusId).isEqualTo(muutosilmoitus.id)
                prop(MuutosilmoitusAttachmentEntity::attachmentType).isEqualTo(typeInput)
                prop(MuutosilmoitusAttachmentEntity::blobLocation)
                    .isNotNull()
                    .startsWith("${muutosilmoitus.hakemusId}/")
            }

            val content =
                fileClient.download(Container.HAKEMUS_LIITTEET, attachments.first().blobLocation)
            assertThat(content)
                .isNotNull()
                .prop(DownloadResponse::content)
                .transform { it.toBytes() }
                .isEqualTo(PDF_BYTES)
        }

        @Test
        fun `sanitizes filenames with special characters`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val muutosilmoitus = muutosilmoitusFactory.builder().save()

            val result =
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.MUU,
                    attachment = testFile(fileName = "exa*mple.pdf"),
                )

            assertThat(result.fileName).isEqualTo("exa_mple.pdf")
            val attachmentInDb = attachmentRepository.getReferenceById(result.id)
            assertThat(attachmentInDb.fileName).isEqualTo("exa_mple.pdf")
        }

        @Test
        fun `throws exception when allowed attachment amount is reached`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()
            val attachments =
                (1..ALLOWED_ATTACHMENT_COUNT).map {
                    MuutosilmoitusAttachmentFactory.createEntity(
                        muutosilmoitusId = muutosilmoitus.id
                    )
                }
            attachmentRepository.saveAll(attachments)
            mockClamAv.enqueue(response(body(results = successResult())))

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    attachment = testFile(),
                )
            }

            failure.all {
                hasClass(AttachmentLimitReachedException::class)
                messageContains("Attachment amount limit reached")
                messageContains("limit=$ALLOWED_ATTACHMENT_COUNT")
                messageContains("Muutosilmoitus: (id=${muutosilmoitus.id}")
            }
        }

        @Test
        fun `throws exception without content`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    attachment = testFile(data = byteArrayOf()),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                messageContains("Attachment has no content")
            }
        }

        @Test
        fun `throws exception without content type`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    attachment = testFile(contentType = null),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                hasMessage("Attachment upload exception: Content-Type null")
            }
        }

        @Test
        fun `throws exception when there is no existing muutosilmoitus`() {
            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = UUID.randomUUID(),
                    attachmentType = ApplicationAttachmentType.MUU,
                    attachment = testFile(),
                )
            }

            failure.hasClass(MuutosilmoitusNotFoundException::class)
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `throws exception when file type is not supported`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()
            val invalidFilename = "hello.html"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
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
        fun `throws exception when file type is not supported for attachment type`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().save()
            val invalidFilename = "hello.jpeg"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    attachment = testFile(fileName = invalidFilename),
                )
            }

            failure.all {
                hasClass(AttachmentInvalidException::class)
                messageContains("File extension is not valid for attachment type")
                messageContains("filename=$invalidFilename")
                messageContains("attachmentType=${ApplicationAttachmentType.VALTAKIRJA}")
            }
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `throws exception when virus scan fails`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val muutosilmoitus = muutosilmoitusFactory.builder().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
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

        @Test
        fun `throws exception when muutosilmoitus has been already sent`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().withSent().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    muutosilmoitusId = muutosilmoitus.id,
                    attachmentType = ApplicationAttachmentType.MUU,
                    attachment = testFile(),
                )
            }

            failure.all {
                hasClass(MuutosilmoitusAlreadySentException::class)
                messageContains("id=${muutosilmoitus.id}")
                messageContains("Muutosilmoitus is already sent to Allu")
            }
            assertThat(attachmentRepository.findAll()).isEmpty()
        }
    }

    @Nested
    inner class DeleteAttachment {
        @Test
        fun `throws exception when attachment is missing`() {
            val attachmentId = UUID.fromString("ab7993b7-a775-4eac-b5b7-8546332944fe")

            val failure = assertFailure { attachmentService.deleteAttachment(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Test
        fun `throws exception when muutosilmoitus has been sent to Allu`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().withSent().save()
            val attachment = attachmentFactory.save(muutosilmoitus = muutosilmoitus)

            val failure = assertFailure {
                attachmentService.deleteAttachment(attachmentId = attachment.id!!)
            }

            failure.all {
                hasClass(MuutosilmoitusAlreadySentException::class)
                messageContains("Muutosilmoitus is already sent to Allu")
                messageContains("id=${muutosilmoitus.id}")
            }
            assertThat(attachmentRepository.findById(attachment.id!!)).isPresent()
        }

        @Test
        fun `deletes attachment and content`() {
            val attachment = attachmentFactory.save()
            assertThat(attachmentRepository.findAll()).hasSize(1)
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET).map { it.path })
                .containsExactly(attachment.blobLocation)

            attachmentService.deleteAttachment(attachment.id!!)

            assertThat(attachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(Container.HAKEMUS_LIITTEET)).isEmpty()
        }
    }
}
