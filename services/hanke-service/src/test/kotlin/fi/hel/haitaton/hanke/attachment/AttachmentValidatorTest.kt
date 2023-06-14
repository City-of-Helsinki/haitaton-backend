package fi.hel.haitaton.hanke.attachment

import assertk.assertThat
import assertk.assertions.contains
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.getResourceAsBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType.IMAGE_GIF_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile

@ExtendWith(OutputCaptureExtension::class)
class AttachmentValidatorTest {

    @Test
    fun validFileName() {
        val fileName = "example.txt"
        AttachmentValidator.validate(testFile(fileName = fileName, contentType = TEXT_PLAIN_VALUE))
    }

    @Test
    fun fileNameMissing(logOutput: CapturedOutput) {
        val ex =
            assertThrows<AttachmentInvalidException> {
                AttachmentValidator.validate(testFile(fileName = null, TEXT_PLAIN_VALUE))
            }

        assertEquals("Attachment upload exception: File '' not supported", ex.message)
        assertThat(logOutput).contains("Attachment file name null or blank")
    }

    @Test
    fun fileNameTooLong(logOutput: CapturedOutput) {
        val fileName = "a".repeat(129)

        val ex =
            assertThrows<AttachmentInvalidException> {
                AttachmentValidator.validate(testFile(fileName = fileName, TEXT_PLAIN_VALUE))
            }

        assertEquals("Attachment upload exception: File '$fileName' not supported", ex.message)
        assertThat(logOutput).contains("File name is too long")
    }

    @Test
    fun invalidCharacters(logOutput: CapturedOutput) {
        val fileName = "exa*mple.txt"

        val ex =
            assertThrows<AttachmentInvalidException> {
                AttachmentValidator.validate(
                    testFile(fileName = fileName, contentType = TEXT_PLAIN_VALUE)
                )
            }

        assertEquals("Attachment upload exception: File '$fileName' not supported", ex.message)
        assertThat(logOutput).contains("File name contains invalid characters")
    }

    @Test
    fun reservedFileName(logOutput: CapturedOutput) {
        val fileName = "con.txt"

        val ex =
            assertThrows<AttachmentInvalidException> {
                AttachmentValidator.validate(
                    testFile(fileName = fileName, contentType = TEXT_PLAIN_VALUE)
                )
            }

        assertEquals("Attachment upload exception: File '$fileName' not supported", ex.message)
        assertThat(logOutput).contains("File name is reserved")
    }

    @Test
    fun pathTraversal(logOutput: CapturedOutput) {
        val fileName = "../example.txt"

        val ex =
            assertThrows<AttachmentInvalidException> {
                AttachmentValidator.validate(
                    testFile(fileName = fileName, contentType = TEXT_PLAIN_VALUE)
                )
            }

        assertEquals("Attachment upload exception: File '$fileName' not supported", ex.message)
        assertThat(logOutput).contains("File name contains path traversal characters")
    }

    @Test
    fun `validate when unsupported type should throw`(logOutput: CapturedOutput) {
        val fileName = "file.gif"

        val ex =
            assertThrows<AttachmentInvalidException> {
                AttachmentValidator.validate(
                    testFile(fileName = fileName, contentType = IMAGE_GIF_VALUE)
                )
            }

        assertEquals("Attachment upload exception: File '$fileName' not supported", ex.message)
        assertThat(logOutput).contains("File 'file.gif' not supported")
    }

    private fun testFile(fileName: String?, contentType: String?): MultipartFile {
        val content = "/fi/hel/haitaton/hanke/attachment/dummy-attachment.pdf".getResourceAsBytes()
        return MockMultipartFile("liite", fileName, contentType, content)
    }
}
