package fi.hel.haitaton.hanke.attachment.taydennys

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
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import assertk.assertions.startsWith
import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container.HAKEMUS_LIITTEET
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadResponse
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.taydennys.TaydennysNotFoundException
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

class TaydennysAttachmentServiceITest(
    @Autowired private val attachmentService: TaydennysAttachmentService,
    @Autowired private val attachmentRepository: TaydennysAttachmentRepository,
    @Autowired private val taydennysFactory: TaydennysFactory,
    @Autowired private val attachmentFactory: TaydennysAttachmentFactory,
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

            val result = attachmentService.getContent(attachment.id!!)

            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.bytes).isEqualTo(PDF_BYTES)
        }

        @Test
        fun `Throws exception when trying to get valtakirja content`() {
            val attachment =
                attachmentFactory
                    .save(attachmentType = ApplicationAttachmentType.VALTAKIRJA)
                    .withContent()
                    .value

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
        fun `Saves attachment metadata to DB and content to blob storage`(
            typeInput: ApplicationAttachmentType
        ) {
            mockClamAv.enqueue(response(body(results = successResult())))
            val taydennys = taydennysFactory.builder().save()

            val result =
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
                    attachmentType = typeInput,
                    attachment = testFile(),
                )

            assertThat(result.id).isNotNull()
            assertThat(result.createdByUserId).isEqualTo(USERNAME)
            assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(result.contentType).isEqualTo(APPLICATION_PDF_VALUE)
            assertThat(result.size).isEqualTo(DEFAULT_SIZE)
            assertThat(result.createdAt).isRecent()
            assertThat(result.taydennysId).isEqualTo(taydennys.id)
            assertThat(result.attachmentType).isEqualTo(typeInput)

            val attachments = attachmentRepository.findAll()
            assertThat(attachments).single().all {
                prop(TaydennysAttachmentEntity::id).isEqualTo(result.id)
                prop(TaydennysAttachmentEntity::createdByUserId).isEqualTo(USERNAME)
                prop(TaydennysAttachmentEntity::fileName).isEqualTo(FILE_NAME_PDF)
                prop(TaydennysAttachmentEntity::contentType).isEqualTo(APPLICATION_PDF_VALUE)
                prop(TaydennysAttachmentEntity::size).isEqualTo(DEFAULT_SIZE)
                prop(TaydennysAttachmentEntity::createdAt).isRecent()
                prop(TaydennysAttachmentEntity::taydennysId).isEqualTo(taydennys.id)
                prop(TaydennysAttachmentEntity::attachmentType).isEqualTo(typeInput)
                prop(TaydennysAttachmentEntity::blobLocation)
                    .isNotNull()
                    .startsWith("${taydennys.hakemusId}/")
            }

            val content = fileClient.download(HAKEMUS_LIITTEET, attachments.first().blobLocation)
            assertThat(content)
                .isNotNull()
                .prop(DownloadResponse::content)
                .transform { it.toBytes() }
                .isEqualTo(PDF_BYTES)
        }

        @Test
        fun `Sanitizes filenames with special characters`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val taydennys = taydennysFactory.builder().save()

            val result =
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
                    attachmentType = ApplicationAttachmentType.MUU,
                    attachment = testFile(fileName = "exa*mple.pdf"),
                )

            assertThat(result.fileName).isEqualTo("exa_mple.pdf")
            val attachmentInDb = attachmentRepository.getReferenceById(result.id)
            assertThat(attachmentInDb.fileName).isEqualTo("exa_mple.pdf")
        }

        @Test
        fun `Throws exception when allowed attachment amount is reached`() {
            val taydennys = taydennysFactory.builder().save()
            val attachments =
                (1..ALLOWED_ATTACHMENT_COUNT).map {
                    TaydennysAttachmentFactory.Companion.createEntity(taydennysId = taydennys.id)
                }
            attachmentRepository.saveAll(attachments)
            mockClamAv.enqueue(response(body(results = successResult())))

            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    attachment = testFile(),
                )
            }

            failure.all {
                hasClass(AttachmentLimitReachedException::class)
                messageContains("Attachment amount limit reached")
                messageContains("limit=$ALLOWED_ATTACHMENT_COUNT")
                messageContains("Täydennys: (id=${taydennys.id}")
            }
        }

        @Test
        fun `Throws exception without content`() {
            val taydennys = taydennysFactory.builder().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
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
        fun `Throws exception without content type`() {
            val taydennys = taydennysFactory.builder().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
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
        fun `Throws exception when there is no existing taydennys`() {
            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = UUID.randomUUID(),
                    attachmentType = ApplicationAttachmentType.MUU,
                    attachment = testFile(),
                )
            }

            failure.hasClass(TaydennysNotFoundException::class)
            assertThat(attachmentRepository.findAll()).isEmpty()
        }

        @Test
        fun `Throws exception when file type is not supported`() {
            val taydennys = taydennysFactory.builder().save()
            val invalidFilename = "hello.html"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
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
        fun `Throws exception when file type is not supported for attachment type`() {
            val taydennys = taydennysFactory.builder().save()
            val invalidFilename = "hello.jpeg"

            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
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
        fun `Throws exception when virus scan fails`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val taydennys = taydennysFactory.builder().save()

            val failure = assertFailure {
                attachmentService.addAttachment(
                    taydennysId = taydennys.id,
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
    }

    @Nested
    inner class DeleteAttachment {
        @Test
        fun `Throws exception when attachment not found`() {
            val attachmentId = UUID.fromString("ab7993b7-a775-4eac-b5b7-8546332944fe")

            val failure = assertFailure { attachmentService.deleteAttachment(attachmentId) }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
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
