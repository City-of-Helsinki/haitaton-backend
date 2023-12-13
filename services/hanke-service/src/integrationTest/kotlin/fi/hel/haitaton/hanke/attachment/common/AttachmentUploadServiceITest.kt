package fi.hel.haitaton.hanke.attachment.common

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class AttachmentUploadServiceITest(
    @Autowired private val attachmentUploadService: AttachmentUploadService,
    @Autowired private val attachmentRepository: HankeAttachmentRepository,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val fileClient: MockFileClient
) : DatabaseTest() {

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

    @Nested
    inner class UploadHankeAttachment {
        @Test
        fun `Should upload blob and return saved metadata`() {
            mockClamAv.enqueue(response(body(results = successResult())))
            val hanke = hankeFactory.save()
            val file = testFile()

            val result =
                attachmentUploadService.uploadHankeAttachment(
                    hankeTunnus = hanke.hankeTunnus,
                    attachment = testFile()
                )

            assertThat(result).all {
                prop(HankeAttachmentMetadataDto::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(HankeAttachmentMetadataDto::createdAt).isRecent()
                prop(HankeAttachmentMetadataDto::createdByUserId).isEqualTo(USERNAME)
                prop(HankeAttachmentMetadataDto::fileName).isEqualTo(file.originalFilename)
            }
            val attachment = attachmentRepository.findById(result.id).orElseThrow()
            val blob = fileClient.download(Container.HANKE_LIITTEET, attachment.blobLocation)
            assertThat(blob.contentType.toString()).isEqualTo(file.contentType)
        }

        @Test
        fun `Should throw when infected file is encountered`() {
            mockClamAv.enqueue(response(body(results = failResult())))
            val hanke = hankeFactory.save()

            assertFailure {
                    attachmentUploadService.uploadHankeAttachment(
                        hankeTunnus = hanke.hankeTunnus,
                        attachment = testFile()
                    )
                }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage(
                        "Attachment upload exception: Infected file detected, see previous logs."
                    )
                }
        }
    }
}
