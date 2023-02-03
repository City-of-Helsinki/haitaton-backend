package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
import fi.hel.haitaton.hanke.attachment.common.FileNameValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FileNameValidatorTest {

    @Test
    fun validFileName() {
        val fileName = "example.txt"
        FileNameValidator.validateFileName(fileName)
    }

    @Test
    fun fileNameTooLong() {
        val fileName = "a".repeat(129)
        val exception =
            assertThrows<AttachmentUploadException> { FileNameValidator.validateFileName(fileName) }
        assertEquals("Attachment upload exception: File name is too long", exception.message)
    }

    @Test
    fun invalidCharacters() {
        val fileName = "exa*mple.txt"
        val exception =
            assertThrows<AttachmentUploadException> { FileNameValidator.validateFileName(fileName) }
        assertEquals(
            "Attachment upload exception: File name contains invalid characters",
            exception.message
        )
    }

    @Test
    fun reservedFileName() {
        val fileName = "con.txt"
        val exception =
            assertThrows<AttachmentUploadException> { FileNameValidator.validateFileName(fileName) }
        assertEquals("Attachment upload exception: File name is reserved", exception.message)
    }

    @Test
    fun pathTraversal() {
        val fileName = "../example.txt"
        val exception =
            assertThrows<AttachmentUploadException> { FileNameValidator.validateFileName(fileName) }
        assertEquals(
            "Attachment upload exception: File name contains path traversal characters",
            exception.message
        )
    }
}
