package fi.hel.haitaton.hanke.attachment.common

import com.azure.core.util.BinaryData
import fi.hel.haitaton.hanke.attachment.azure.Container
import java.nio.charset.StandardCharsets
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType

interface FileClient {

    fun upload(
        container: Container,
        path: String,
        originalFilename: String,
        contentType: MediaType,
        content: ByteArray,
    )

    @Throws(DownloadNotFoundException::class)
    fun download(container: Container, path: String): DownloadResponse

    fun delete(container: Container, path: String): Boolean

    fun deleteAllByPrefix(container: Container, prefix: String)

    /**
     * Encodes a filename for use in the Content-Disposition header according to RFC 5987.
     *
     * Uses Spring's ContentDisposition builder which properly handles RFC 5987 encoding for special
     * characters like commas, semicolons, quotes, asterisks, etc. that have special meaning in HTTP
     * headers.
     */
    fun encodeFilename(filename: String): String {
        return ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString()
            .substringAfter("filename*=UTF-8''")
    }
}

data class DownloadResponse(
    val contentType: MediaType,
    val contentLength: Int,
    val content: BinaryData,
)

data class DownloadNotFoundException(val path: String, val container: Container) :
    RuntimeException("Downloaded file was not found, path=$path, container=$container")
