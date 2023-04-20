package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.clearAllMocks
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val username = "username"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class AttachmentServiceITests() : DatabaseTest() {
    @Autowired private lateinit var attachmentService: AttachmentService
    @Autowired private lateinit var attachmentRepository: AttachmentRepository
    @Autowired private lateinit var hankeService: HankeService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @Test
    @WithMockUser(username)
    fun `Saving an attachment is possible`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile("tiedosto.pdf", null, "application/pdf", byteArrayOf(1, 2, 3, 4))
            )

        assertThat(result.id).isNotNull
        assertThat(result.createdByUserId).isEqualTo(username)
        assertThat(result.name).isEqualTo("tiedosto.pdf")
        assertThat(result.createdAt).isNotNull
        assertThat(result.hankeTunnus).isEqualTo(createdHanke.hankeTunnus)
        assertThat(result.scanStatus)
            .isEqualTo(AttachmentScanStatus.OK) // FIXME should be PENDING with virus scan
    }

    @Test
    @WithMockUser(username)
    fun `Attachments can't be saved without hanke`() {
        assertThrows<AttachmentUploadError> {
            attachmentService.add(
                "",
                MockMultipartFile("tiedosto.pdf", "tiedosto.pdf", "application/pdf", null)
            )
        }
    }

    @Test
    @WithMockUser(username)
    fun `File extension must match content type`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val invalidFilename = "hello.html"

        val ex =
            assertThrows<AttachmentUploadError> {
                attachmentService.add(
                    createdHanke.hankeTunnus!!,
                    MockMultipartFile(
                        invalidFilename,
                        invalidFilename,
                        "application/pdf",
                        byteArrayOf(1, 2, 3, 4)
                    )
                )
            }

        assertThat(ex.message)
            .isEqualTo(
                "Attachment upload error: File extension does not match content type application/pdf"
            )
    }

    @Test
    @WithMockUser(username)
    fun `Filenames are sanitized`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val invalidFilename = " \t$=hei=ö£@.pdf "

        val file =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile(
                    invalidFilename,
                    invalidFilename,
                    "application/pdf",
                    byteArrayOf(1, 2, 3, 4)
                )
            )

        assertThat(file.name).isEqualTo("heiö.pdf")
    }

    @Test
    @WithMockUser(username)
    fun `Non supported file cannot be uploaded`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        assertThrows<AttachmentUploadError> {
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile("tiedosto.html", "tiedosto.html", "text/html", null)
            )
        }
    }

    @Test
    @WithMockUser(username)
    fun `Can't upload files bigger than 10Mb`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        attachmentService.add(
            createdHanke.hankeTunnus!!,
            MockMultipartFile(
                "tiedosto.pdf",
                "tiedosto.pdf",
                "application/pdf",
                ByteArray(1024 * 1024 * 10)
            )
        )

        assertThrows<AttachmentUploadError> {
            attachmentService.add(
                hanke.hankeTunnus!!,
                MockMultipartFile(
                    "tiedosto.pdf",
                    "tiedosto.pdf",
                    "application/pdf",
                    ByteArray(1024 * 1024 * 11)
                )
            )
        }
    }

    @Test
    @WithMockUser(username)
    fun `Hanke data is downloadable when data is ok`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile("tiedosto.pdf", null, "application/pdf", byteArrayOf(1, 2, 3, 4))
            )

        val data = attachmentService.getContent(result.id!!)

        assertThat(data).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    @WithMockUser(username)
    fun `Hanke data not downloadable when state is PENDING`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile("tiedosto.pdf", null, "application/pdf", byteArrayOf(1, 2, 3, 4))
            )
        val att = attachmentRepository.getOne(result.id!!)
        att.scanStatus = AttachmentScanStatus.PENDING
        attachmentRepository.save(att)

        assertThrows<AttachmentNotFoundException>() { attachmentService.getContent(result.id!!) }
    }

    @Test
    @WithMockUser(username)
    fun `Hanke data not downloadable when state is FAILED`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile("tiedosto.pdf", null, "application/pdf", byteArrayOf(1, 2, 3, 4))
            )
        val att = attachmentRepository.getOne(result.id!!)
        att.scanStatus = AttachmentScanStatus.FAILED
        attachmentRepository.save(att)

        assertThrows<AttachmentNotFoundException>() { attachmentService.getContent(result.id!!) }
    }

    @Test
    @WithMockUser(username)
    fun `Removing an attachment is possible`() {
        val hanke = HankeFactory.create()
        val createdHanke = hankeService.createHanke(hanke)
        val result =
            attachmentService.add(
                createdHanke.hankeTunnus!!,
                MockMultipartFile("tiedosto.pdf", null, "application/pdf", byteArrayOf(1, 2, 3, 4))
            )

        attachmentService.removeAttachment(result.id!!)

        assertThrows<AttachmentNotFoundException> { attachmentService.get(result.id!!) }
    }
}
