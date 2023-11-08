package fi.hel.haitaton.hanke.attachment.common

import com.azure.core.util.BinaryData
import fi.hel.haitaton.hanke.attachment.azure.Container
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
}

data class DownloadResponse(
    val contentType: MediaType,
    val contentLength: Int,
    val content: BinaryData,
)

data class DownloadNotFoundException(val path: String, val container: Container) :
    RuntimeException("Downloaded file was not found, path=$path, container=$container")
