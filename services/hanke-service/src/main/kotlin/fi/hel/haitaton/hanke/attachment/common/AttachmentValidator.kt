package fi.hel.haitaton.hanke.attachment.common

import java.util.Locale
import java.util.regex.Pattern
import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.FilenameUtils.removeExtension
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

private val supportedFiletypes =
    setOf(
        "pdf",
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

    private val INVALID_CHARS_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]")

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

    fun validate(attachment: MultipartFile) =
        with(attachment) {
            logger.info { "Validating file $originalFilename of type: $contentType" }
            if (contentType.isNullOrBlank() || !validFileName(originalFilename)) {
                throw AttachmentInvalidException("File '$originalFilename' not supported")
            }
        }

    private fun validFileName(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) {
            logger.warn { "Attachment file name null or blank" }
            return false
        }

        if (fileName.length > 128) {
            logger.warn { "File name is too long" }
            return false
        }

        if (!supportedType(fileName)) {
            logger.warn { "File '$fileName' not supported" }
            return false
        }

        if (fileName.contains("..")) {
            logger.warn { "File name contains path traversal characters" }
            return false
        }

        if (INVALID_CHARS_PATTERN.matcher(fileName).find()) {
            logger.warn { "File name contains invalid characters" }
            return false
        }

        if (RESERVED_NAMES.contains(removeExtension(fileName).uppercase(Locale.getDefault()))) {
            logger.warn { "File name is reserved" }
            return false
        }

        return true
    }

    private fun supportedType(fileName: String): Boolean {
        val extension = getExtension(fileName)
        return supportedFiletypes.contains(extension)
    }
}
