package fi.hel.haitaton.hanke.attachment.common

import java.util.Locale
import java.util.regex.Pattern
import mu.KotlinLogging
import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.FilenameUtils.removeExtension
import org.apache.tika.Tika
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

private val supportedFiletypes =
    mapOf(
        "application/pdf" to setOf("pdf"),
        "application/msword" to setOf("docx", "doc"),
        "image/jpeg" to setOf("jpg", "jpeg"),
        "image/png" to setOf("png"),
        "image/vnd.dwg" to setOf("dwg", "dws")
    )

object AttachmentValidator {
    fun validate(attachment: MultipartFile) =
        with(attachment) {
            FileNameValidator.validate(originalFilename)
            if (!validContentType(this)) {
                throw AttachmentUploadException(
                    "File '$originalFilename' does not match type '$contentType'"
                )
            }
        }

    private fun validContentType(attachment: MultipartFile): Boolean =
        with(attachment) {
            val deduced = deducedType(bytes)
            if (contentType.isNullOrBlank() || deduced != contentType) {
                logger.info { "Validation fail, claimed: $contentType, deduced: $deduced" }
                return false
            }
            val extension = getExtension(originalFilename)
            return supportedFiletypes[contentType]?.contains(extension) ?: false
        }

    private fun deducedType(data: ByteArray): String = Tika().detect(data)
}

// Microsoft: https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file
// Linux: https://www.tldp.org/LDP/intro-linux/html/sect_03_01.html
object FileNameValidator {

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
            "LPT9"
        )

    fun validate(fileName: String?) {
        if (fileName.isNullOrBlank()) {
            throw AttachmentUploadException("Attachment file name null or blank")
        }

        if (fileName.length > 128) {
            throw AttachmentUploadException("File name is too long")
        }

        if (fileName.contains("..")) {
            throw AttachmentUploadException("File name contains path traversal characters")
        }

        if (INVALID_CHARS_PATTERN.matcher(fileName).find()) {
            throw AttachmentUploadException("File name contains invalid characters")
        }

        if (RESERVED_NAMES.contains(removeExtension(fileName).uppercase(Locale.getDefault()))) {
            throw AttachmentUploadException("File name is reserved")
        }
    }
}
