package fi.hel.haitaton.hanke.attachment.common

import java.util.Locale
import java.util.regex.Pattern
import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.FilenameUtils.removeExtension
import org.springframework.web.multipart.MultipartFile

private val supportedFiletypes =
    mapOf(
        "application/pdf" to setOf("pdf"),
        "application/msword" to setOf("docx", "doc"),
        "image/jpeg" to setOf("jpg", "jpeg"),
        "image/png" to setOf("png"),
        "image/vnd.dwg" to setOf("dwg", "dws")
    )

object AttachmentValidator {
    fun validate(input: MultipartFile): MultipartFile {
        val fileName = FileNameValidator.validateFileName(input.originalFilename)

        if (!contentTypeMatchesExtension(input.contentType, getExtension(fileName))) {
            throw AttachmentUploadException(
                "File '$fileName' extension does not match content type ${input.contentType}"
            )
        }

        return input
    }

    private fun contentTypeMatchesExtension(contentType: String?, extension: String): Boolean {
        if (contentType.isNullOrBlank()) {
            return false
        }
        return supportedFiletypes[contentType]?.contains(extension) ?: false
    }
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

    fun validateFileName(input: String?): String {
        if (input.isNullOrBlank()) {
            throw AttachmentUploadException("Attachment file name null or blank")
        }

        if (input.length > 128) {
            throw AttachmentUploadException("File name is too long")
        }

        if (input.contains("..")) {
            throw AttachmentUploadException("File name contains path traversal characters")
        }

        if (INVALID_CHARS_PATTERN.matcher(input).find()) {
            throw AttachmentUploadException("File name contains invalid characters")
        }

        if (RESERVED_NAMES.contains(removeExtension(input).uppercase(Locale.getDefault()))) {
            throw AttachmentUploadException("File name is reserved")
        }

        return input
    }
}
