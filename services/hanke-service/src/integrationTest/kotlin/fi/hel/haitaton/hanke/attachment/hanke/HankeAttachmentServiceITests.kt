package fi.hel.haitaton.hanke.attachment.hanke

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.dummyData
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.util.Optional
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.TEXT_HTML_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(USERNAME)
class HankeAttachmentServiceITests : DatabaseTest() {
    @Autowired private lateinit var hankeAttachmentService: HankeAttachmentService
    @Autowired private lateinit var hankeAttachmentRepository: HankeAttachmentRepository
    @Autowired private lateinit var hankeService: HankeService

    @Test
    fun `getMetadataList should return related metadata list`() {
        val hanke = hankeService.createHanke(HankeFactory.create())
        (1..2).forEach { _ ->
            hankeAttachmentService.addAttachment(
                hankeTunnus = hanke.hankeTunnus!!,
                attachment = testFile()
            )
        }

        val result = hankeAttachmentService.getMetadataList(hanke.hankeTunnus!!)

        assertThat(result).hasSize(2)
        assertThat(result).each { d ->
            d.transform { it.id }.isNotNull()
            d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
            d.transform { it.createdByUserId }.isEqualTo(USERNAME)
            d.transform { it.createdAt }.isNotNull()
            d.transform { it.scanStatus }.isEqualTo(OK)
            d.transform { it.hankeTunnus }.isEqualTo(hanke.hankeTunnus)
        }
    }

    @Test
    fun `getContent when status is OK should succeed`() {
        val hanke = hankeService.createHanke(HankeFactory.create())

        val result = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())

        hankeAttachmentService.getContent(hanke.hankeTunnus!!, result.id!!).let {
            (fileName, content) ->
            assertThat(fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(content).isEqualTo(dummyData)
        }
    }

    @Test
    fun `getContent when attachment is not in requested hanke should throw`() {
        val firstHanke = hankeService.createHanke(HankeFactory.create())
        val secondHanke = hankeService.createHanke(HankeFactory.create())
        hankeAttachmentService.addAttachment(
            hankeTunnus = firstHanke.hankeTunnus!!,
            attachment = testFile(),
        )
        val secondAttachment =
            hankeAttachmentService.addAttachment(
                hankeTunnus = secondHanke.hankeTunnus!!,
                attachment = testFile(),
            )

        val exception =
            assertThrows<AttachmentNotFoundException> {
                hankeAttachmentService.getContent(firstHanke.hankeTunnus!!, secondAttachment.id!!)
            }

        assertThat(exception.message).isEqualTo("Attachment ${secondAttachment.id} not found")
    }

    @Test
    fun `addAttachment when valid input returns metadata of saved attachment`() {
        val hanke = hankeService.createHanke(HankeFactory.create())

        val result = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())

        assertThat(result.id).isNotNull()
        assertThat(result.createdByUserId).isEqualTo(USERNAME)
        assertThat(result.fileName).isEqualTo(FILE_NAME_PDF)
        assertThat(result.createdAt).isNotNull()
        assertThat(result.hankeTunnus).isEqualTo(hanke.hankeTunnus)
        assertThat(result.scanStatus).isEqualTo(OK)
    }

    @Test
    fun `addAttachment when no related hanke should fail`() {
        assertThrows<HankeNotFoundException> {
            hankeAttachmentService.addAttachment("", testFile())
        }
    }

    @Test
    fun `addAttachment when content type does not match file extension should fail`() {
        val hanke = hankeService.createHanke(HankeFactory.create())
        val invalidFilename = "hello.html"

        val ex =
            assertThrows<AttachmentUploadException> {
                hankeAttachmentService.addAttachment(
                    hanke.hankeTunnus!!,
                    testFile(fileName = invalidFilename),
                )
            }

        assertThat(ex.message)
            .isEqualTo(
                "Attachment upload exception: File '$invalidFilename' extension does not match content type application/pdf"
            )
    }

    @Test
    fun `addAttachment when not supported content type should fail`() {
        val hanke = hankeService.createHanke(HankeFactory.create())

        assertThrows<AttachmentUploadException> {
            hankeAttachmentService.addAttachment(
                hanke.hankeTunnus!!,
                testFile(contentType = TEXT_HTML_VALUE)
            )
        }
    }

    @EnumSource(value = AttachmentScanStatus::class, names = ["PENDING", "FAILED"])
    @ParameterizedTest
    fun `getContent when status is not OK should throw`(status: AttachmentScanStatus) {
        val hanke = hankeService.createHanke(HankeFactory.create())
        val result = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())
        val attachment = hankeAttachmentRepository.findById(result.id!!).orElseThrow()
        attachment.scanStatus = status
        hankeAttachmentRepository.save(attachment)

        assertThrows<AttachmentNotFoundException> {
            hankeAttachmentService.getContent(hanke.hankeTunnus!!, result.id!!)
        }
    }

    @Test
    fun `deleteAttachment when valid input should succeed`() {
        val hanke = hankeService.createHanke(HankeFactory.create())
        val result = hankeAttachmentService.addAttachment(hanke.hankeTunnus!!, testFile())
        val attachmentId = result.id!!
        assertThat(hankeAttachmentRepository.findById(attachmentId).orElseThrow()).isNotNull()

        hankeAttachmentService.deleteAttachment(hanke.hankeTunnus!!, attachmentId)

        assertThat(hankeAttachmentRepository.findById(attachmentId)).isEqualTo(Optional.empty())
    }
}
