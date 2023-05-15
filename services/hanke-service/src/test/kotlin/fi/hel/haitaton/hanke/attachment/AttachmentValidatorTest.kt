package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.getResourceAsBytes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.MediaType.IMAGE_GIF_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

class AttachmentValidatorTest {

    @Test
    fun `validate matching content type and extension should pass`() {
        val attachment = testFile("file.pdf", APPLICATION_PDF_VALUE)
        assertDoesNotThrow { AttachmentValidator.validate(attachment) }
    }

    @Test
    fun `validate when attachment with mismatched content type and extension should throw`() {
        val attachment = testFile("file.png", APPLICATION_PDF_VALUE)
        assertThrows<AttachmentUploadException> { AttachmentValidator.validate(attachment) }
    }

    @Test
    fun `validate when attachment with null or blank content type should throw`() {
        val attachment1 = testFile("file.pdf", null)
        val attachment2 = testFile("file.docx", "")
        assertThrows<AttachmentUploadException> { AttachmentValidator.validate(attachment1) }
        assertThrows<AttachmentUploadException> { AttachmentValidator.validate(attachment2) }
    }

    @Test
    fun `validate when attachment with unsupported content type should throw`() {
        val attachment = testFile("file.txt", IMAGE_GIF_VALUE)
        assertThrows<AttachmentUploadException> { AttachmentValidator.validate(attachment) }
    }

    private fun testFile(fileName: String, contentType: String?): MultipartFile {
        val content = "/fi/hel/haitaton/hanke/attachment/dummy-attachment.pdf".getResourceAsBytes()
        return MockMultipartFile(fileName, fileName, contentType, content)
    }
}
