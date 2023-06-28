package fi.hel.haitaton.hanke.attachment

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class AttachmentValidatorTest {

    @Test
    fun validFileName() {
        val filename = "example.txt"

        val result = AttachmentValidator.validFilename(filename)

        assertThat(result).isEqualTo(filename)
    }

    @Test
    fun `valid filename in uppercase`() {
        val filename = "EXAMPLE.TXT"

        val result = AttachmentValidator.validFilename(filename)

        assertThat(result).isEqualTo(filename)
    }

    @Test
    fun fileNameMissing(logOutput: CapturedOutput) {
        val ex = assertThrows<AttachmentInvalidException> { AttachmentValidator.validFilename("") }

        assertEquals("Attachment upload exception: File '' not supported", ex.message)
        assertThat(logOutput).contains("Attachment file name null or blank")
    }

    @Test
    fun fileNameTooLong(logOutput: CapturedOutput) {
        val filename = "a".repeat(129)

        val ex =
            assertThrows<AttachmentInvalidException> { AttachmentValidator.validFilename(filename) }

        assertEquals("Attachment upload exception: File '$filename' not supported", ex.message)
        assertThat(logOutput).contains("File name is too long")
    }

    @ParameterizedTest
    @CsvSource(
        "exa\\mple,exa_mple",
        "exa/mple,exa_mple",
        "example:,example_",
        "exa*mple,exa_mple",
        "examp?le,examp_le",
        "e\"xample,e_xample",
        "exa<mple,exa_mple",
        ">example,_example",
        "example|,example_",
        "exa%22mple,exa_mple",
        "exa*|%22:<>mple,exa______mple",
        "exa-mple,exa-mple",
    )
    fun invalidCharacters(given: String, expected: String) {
        val filename = "$given.txt"

        val result = AttachmentValidator.validFilename(filename)

        assertThat(result).isEqualTo("$expected.txt")
    }

    @Test
    fun reservedFileName(logOutput: CapturedOutput) {
        val filename = "con.txt"

        val ex =
            assertThrows<AttachmentInvalidException> { AttachmentValidator.validFilename(filename) }

        assertEquals("Attachment upload exception: File '$filename' not supported", ex.message)
        assertThat(logOutput).contains("File name is reserved")
    }

    @Test
    fun pathTraversal(logOutput: CapturedOutput) {
        val filename = "../example.txt"

        val ex =
            assertThrows<AttachmentInvalidException> { AttachmentValidator.validFilename(filename) }

        assertEquals("Attachment upload exception: File '.._example.txt' not supported", ex.message)
        assertThat(logOutput).contains("File name contains path traversal characters")
    }

    @Test
    fun `validate when unsupported type should throw`(logOutput: CapturedOutput) {
        val filename = "file.gif"

        val ex =
            assertThrows<AttachmentInvalidException> { AttachmentValidator.validFilename(filename) }

        assertEquals("Attachment upload exception: File '$filename' not supported", ex.message)
        assertThat(logOutput).contains("File 'file.gif' not supported")
    }
}
