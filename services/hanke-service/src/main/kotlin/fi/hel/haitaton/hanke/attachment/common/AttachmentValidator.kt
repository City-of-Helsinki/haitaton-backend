package fi.hel.haitaton.hanke.attachment.common

import java.io.File
import mu.KotlinLogging
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

private const val pdfExtension = "pdf"

private val supportedFiletypes =
    setOf(pdfExtension, "jpg", "jpeg", "png", "dgn", "dwg", "docx", "txt", "gt")

fun MultipartFile.validNameAndType() =
    Pair(
        AttachmentValidator.validFilename(originalFilename),
        AttachmentValidator.ensureMediaType(contentType),
    )

// Microsoft: https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file
// Linux: https://www.tldp.org/LDP/intro-linux/html/sect_03_01.html
object AttachmentValidator {

    /** %22 is what `"` gets encoded to for transport. Treating it like it's `"`. */
    private val INVALID_CHARS_PATTERN = "%22|[\\\\/:*?.\"<>|]".toRegex()

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

    fun validateSize(size: Int) {
        if (size == 0) throw AttachmentInvalidException("Attachment has no content.")
    }

    fun validFilename(filename: String?): String {
        if (filename == null) {
            throw AttachmentInvalidException("Null filename not supported")
        }

        logger.info { "Validating file name $filename" }

        val sanitizedFilename = sanitizeFilename(filename)
        logger.info { "Sanitized file name to $sanitizedFilename" }

        if (!isValidFilename(sanitizedFilename)) {
            throw AttachmentInvalidException("File '$sanitizedFilename' not supported")
        }
        return sanitizedFilename
    }

    fun ensureMediaType(contentType: String?): MediaType =
        contentType?.let { parseMediaType(it) }
            ?: throw AttachmentInvalidException("Content-Type null")

    fun parseMediaType(type: String): MediaType =
        try {
            MediaType.parseMediaType(type)
        } catch (e: InvalidMediaTypeException) {
            throw AttachmentInvalidException("Invalid content type, $type")
        }

    fun validateExtensionForType(
        sanitizedFilename: String,
        attachmentType: ApplicationAttachmentType,
    ) {
        if (!isValidExtensionForType(sanitizedFilename, attachmentType)) {
            throw AttachmentInvalidException(
                "File extension is not valid for attachment type. filename=$sanitizedFilename, attachmentType=$attachmentType"
            )
        }
    }

    private fun sanitizeFilename(filename: String): String {
        val extension = filename.extension.replace(INVALID_CHARS_PATTERN, "_")
        val base = filename.withoutExtension.replace(INVALID_CHARS_PATTERN, "_")

        return if (extension.isEmpty()) base else "$base.$extension"
    }

    private fun isValidFilename(filename: String): Boolean =
        when {
            filename.isBlank() -> fail("Attachment file name blank")
            filename.length > 128 -> fail("File name is too long")
            !supportedType(filename) -> fail("File '$filename' not supported")
            RESERVED_NAMES.contains(filename.withoutExtension.uppercase()) ->
                fail("File name is reserved")
            else -> true
        }

    private fun fail(reason: String): Boolean {
        logger.warn { reason }
        return false
    }

    private fun supportedType(filename: String): Boolean {
        val extension = filename.extension.lowercase()
        return supportedFiletypes.contains(extension)
    }

    private fun isValidExtensionForType(
        filename: String,
        attachmentType: ApplicationAttachmentType,
    ): Boolean {
        val extension = filename.extension.lowercase()
        return when (attachmentType) {
            ApplicationAttachmentType.MUU -> supportedFiletypes.contains(extension)
            ApplicationAttachmentType.VALTAKIRJA,
            ApplicationAttachmentType.LIIKENNEJARJESTELY -> extension == pdfExtension
        }
    }

    /** Using File removes the path part from the name. */
    private val String.extension: String
        get() = File(this).extension

    /** Using File removes the path part from the name. */
    private val String.withoutExtension: String
        get() = File(this).nameWithoutExtension
}
