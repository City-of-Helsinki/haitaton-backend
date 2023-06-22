package fi.hel.haitaton.hanke.attachment.common

import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.FilenameUtils.removeExtension

private val logger = KotlinLogging.logger {}

private val supportedFiletypes =
    setOf(
        "pdf",
        "jpg",
        "jpeg",
        "png",
        "dgn",
        "dwg",
        "docx",
        "txt",
        "gt",
    )

// Microsoft: https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file
// Linux: https://www.tldp.org/LDP/intro-linux/html/sect_03_01.html
object AttachmentValidator {

    /** %22 is what `"` gets encoded to for transport. Treating it like it's `"`. */
    private val INVALID_CHARS_PATTERN = "%22|[\\\\/:*?\"<>|]".toRegex()

    private val RESERVED_NAMES =
        arrayOf(
            "CON",
            "PRN",
            "AUX",
            "NUL",
            "COM1",
            "COM2",
            "COM3",
            "COM4",
            "COM5",
            "COM6",
            "COM7",
            "COM8",
            "COM9",
            "LPT1",
            "LPT2",
            "LPT3",
            "LPT4",
            "LPT5",
            "LPT6",
            "LPT7",
            "LPT8",
            "LPT9",
        )

    fun validFilename(originalFilename: String?): String {
        logger.info { "Validating file name $originalFilename" }

        val sanitizedFilename = sanitizeFilename(originalFilename)
        logger.info { "Sanitized file name to $sanitizedFilename" }

        if (!isValidFilename(sanitizedFilename)) {
            throw AttachmentInvalidException("File '$sanitizedFilename' not supported")
        }
        return sanitizedFilename!!
    }

    private fun sanitizeFilename(filename: String?): String? =
        filename?.replace(INVALID_CHARS_PATTERN, "_")

    private fun isValidFilename(filename: String?): Boolean =
        when {
            filename.isNullOrBlank() -> fail("Attachment file name null or blank")
            filename.length > 128 -> fail("File name is too long")
            !supportedType(filename) -> fail("File '$filename' not supported")
            filename.contains("..") -> fail("File name contains path traversal characters")
            INVALID_CHARS_PATTERN.containsMatchIn(filename) ->
                fail("File name contains invalid characters")
            RESERVED_NAMES.contains(removeExtension(filename).uppercase()) ->
                fail("File name is reserved")
            else -> true
        }

    private fun fail(reason: String): Boolean {
        logger.warn { reason }
        return false
    }

    private fun supportedType(filename: String): Boolean {
        val extension = getExtension(filename)
        return supportedFiletypes.contains(extension.lowercase())
    }
}
