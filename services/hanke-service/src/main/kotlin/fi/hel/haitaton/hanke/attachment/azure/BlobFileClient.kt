package fi.hel.haitaton.hanke.attachment.azure

import com.azure.core.http.HttpHeaderName
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobErrorCode
import com.azure.storage.blob.models.BlobHttpHeaders
import com.azure.storage.blob.models.BlobStorageException
import com.azure.storage.blob.options.BlobParallelUploadOptions
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadResponse
import fi.hel.haitaton.hanke.attachment.common.FileClient
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
@Profile("!test")
class BlobFileClient(blobServiceClient: BlobServiceClient, containers: Containers) : FileClient {

    @EventListener(ApplicationReadyEvent::class)
    fun startup() {
        logger.info { "Getting a list of blobs in hanke attachments..." }
        val blobs = hankeAttachmentClient.listBlobs().toList()
        logger.info { "Found ${blobs.size} blobs:" }
        blobs.forEachIndexed { i, blob ->
            logger.info {
                "${i+1} ${blob.name}, ${blob.properties.contentLength},${blob.properties.contentType}, ${blob.properties.contentDisposition}"
            }
        }
        logger.info { "End of blob list." }
    }

    private val decisionClient = blobServiceClient.getBlobContainerClient(containers.decisions)

    private val hakemusAttachmentClient =
        blobServiceClient.getBlobContainerClient(containers.hakemusAttachments)

    private val hankeAttachmentClient =
        blobServiceClient.getBlobContainerClient(containers.hankeAttachments)

    override fun upload(
        container: Container,
        path: String,
        originalFilename: String,
        contentType: MediaType,
        content: ByteArray,
    ) {
        val options = BlobParallelUploadOptions(BinaryData.fromBytes(content))
        options.headers = BlobHttpHeaders()
        options.headers.setContentType(contentType.toString())
        options.headers.setContentDisposition("attachment; filename=$originalFilename")
        getContainerClient(container).getBlobClient(path).uploadWithResponse(options, null, null)
    }

    override fun download(container: Container, path: String): DownloadResponse {
        try {
            val response =
                getContainerClient(container)
                    .getBlobClient(path)
                    .downloadContentWithResponse(null, null, null, null)

            val contentType = response.headers[HttpHeaderName.CONTENT_TYPE].value
            val contentLength = response.headers[HttpHeaderName.CONTENT_LENGTH].value

            return DownloadResponse(
                MediaType.parseMediaType(contentType),
                contentLength.toInt(),
                response.value
            )
        } catch (e: BlobStorageException) {
            if (e.errorCode == BlobErrorCode.BLOB_NOT_FOUND) {
                logger.error { "Blob not found, container=$container path=$path" }
                throw DownloadNotFoundException(path, container)
            } else {
                throw e
            }
        }
    }

    override fun delete(container: Container, path: String): Boolean =
        getContainerClient(container).getBlobClient(path).deleteIfExists()

    private fun getContainerClient(container: Container) =
        when (container) {
            Container.HAKEMUS_LIITTEET -> hakemusAttachmentClient
            Container.HANKE_LIITTEET -> hankeAttachmentClient
            Container.PAATOKSET -> decisionClient
        }
}
