package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.clearAllMocks
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.byLessThan
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.MediaType.TEXT_HTML_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERNAME = "username"
private const val FILE_NAME = "tiedosto.pdf"
private const val FILE_PARAM = "liite"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class AttachmentServiceITests : DatabaseTest() {
    @Autowired private lateinit var attachmentService: AttachmentService
    @Autowired private lateinit var hankeAttachmentRepository: HankeAttachmentRepository
    @Autowired private lateinit var hankeService: HankeService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Saving an attachment is possible`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(
                    FILE_PARAM,
                    FILE_NAME,
                    APPLICATION_PDF_VALUE,
                    byteArrayOf(1, 2, 3, 4)
                )
            )

        val now = OffsetDateTime.now()
        assertThat(result.id).isNotNull
        assertThat(result.createdByUserId).isEqualTo(USERNAME)
        assertThat(result.fileName).isEqualTo(FILE_NAME)
        assertThat(result.createdAt).isBefore(now).isCloseTo(now, byLessThan(1, ChronoUnit.SECONDS))
        assertThat(result.hankeTunnus).isEqualTo(createdHanke.hankeTunnus)
        assertThat(result.scanStatus)
            .isEqualTo(AttachmentScanStatus.OK) // FIXME should be PENDING with virus scan
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Attachments can't be saved without hanke`() {
        assertThrows<AttachmentUploadException> {
            attachmentService.add(
                "",
                MockMultipartFile(FILE_PARAM, FILE_NAME, APPLICATION_PDF_VALUE, null)
            )
        }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `File extension must match content type`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val invalidFilename = "hello.html"

        val ex =
            assertThrows<AttachmentUploadException> {
                attachmentService.add(
                    createdHanke.hankeTunnus!!,
                    MockMultipartFile(
                        invalidFilename,
                        invalidFilename,
                        APPLICATION_PDF_VALUE,
                        byteArrayOf(1, 2, 3, 4)
                    )
                )
            }

        assertThat(ex.message)
            .isEqualTo(
                "Attachment upload exception: File '$invalidFilename' extension does not match content type application/pdf"
            )
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Non supported file cannot be uploaded`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        assertThrows<AttachmentUploadException> {
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(FILE_PARAM, FILE_NAME, TEXT_HTML_VALUE, null)
            )
        }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Can't upload files bigger than 25Mb`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        attachmentService.add(
            createdHanke.hankeTunnus!!,
            MockMultipartFile(
                FILE_PARAM,
                FILE_NAME,
                APPLICATION_PDF_VALUE,
                ByteArray(1024 * 1024 * 10)
            )
        )

        assertThrows<AttachmentUploadException> {
            attachmentService.add(
                hanke.hankeTunnus!!,
                MockMultipartFile(
                    FILE_PARAM,
                    FILE_NAME,
                    APPLICATION_PDF_VALUE,
                    ByteArray(1024 * 1024 * 26)
                )
            )
        }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Hanke data is downloadable when data is ok`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(
                    FILE_PARAM,
                    FILE_NAME,
                    APPLICATION_PDF_VALUE,
                    byteArrayOf(1, 2, 3, 4)
                )
            )

        val data = attachmentService.getContent(result.id!!)

        assertThat(data).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Hanke data not downloadable when state is PENDING`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(
                    FILE_PARAM,
                    FILE_NAME,
                    APPLICATION_PDF_VALUE,
                    byteArrayOf(1, 2, 3, 4)
                )
            )
        val att = hankeAttachmentRepository.getOne(result.id!!)
        att.scanStatus = AttachmentScanStatus.PENDING
        hankeAttachmentRepository.save(att)

        assertThrows<AttachmentNotFoundException> { attachmentService.getContent(result.id!!) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Hanke data not downloadable when state is FAILED`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(
                    FILE_PARAM,
                    FILE_NAME,
                    APPLICATION_PDF_VALUE,
                    byteArrayOf(1, 2, 3, 4)
                )
            )
        val att = hankeAttachmentRepository.getOne(result.id!!)
        att.scanStatus = AttachmentScanStatus.FAILED
        hankeAttachmentRepository.save(att)

        assertThrows<AttachmentNotFoundException> { attachmentService.getContent(result.id!!) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Removing an attachment is possible`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(
                    FILE_PARAM,
                    FILE_NAME,
                    APPLICATION_PDF_VALUE,
                    byteArrayOf(1, 2, 3, 4)
                )
            )

        attachmentService.removeAttachment(result.id!!)

        assertThrows<AttachmentNotFoundException> { attachmentService.getMetadata(result.id!!) }
    }
}
