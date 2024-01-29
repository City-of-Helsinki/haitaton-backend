package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentContentService(
    val fileClient: FileClient,
) {
    fun upload(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        applicationId: Long
    ): String {
        val blobPath = generateBlobPath(applicationId)
        fileClient.upload(Container.HAKEMUS_LIITTEET, blobPath, filename, contentType, content)
        logger.info { "Attachment content saved to $blobPath" }
        return blobPath
    }

    fun delete(blobPath: String): Boolean =
        fileClient.delete(Container.HAKEMUS_LIITTEET, blobPath).also {
            if (it) {
                logger.info { "Attachment content at $blobPath deleted" }
            } else {
                logger.warn { "Attachment content at $blobPath not found" }
            }
        }

    fun deleteAllForApplication(applicationId: Long) {
        fileClient.deleteAllByPrefix(Container.HAKEMUS_LIITTEET, prefix(applicationId))
        logger.info { "Deleted all attachment content from application $applicationId" }
    }

    fun find(attachment: ApplicationAttachmentMetadata): ByteArray =
        try {
            fileClient
                .download(Container.HAKEMUS_LIITTEET, attachment.blobLocation)
                .content
                .toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachment.id)
        }

    companion object {
        fun generateBlobPath(applicationId: Long) =
            "${prefix(applicationId)}${UUID.randomUUID()}"
                .also { logger.info { "Generated blob path: $it" } }

        /**
         * Prefix derived from application each attachment of that application should have in the
         * cloud storage.
         *
         * Used to distinguish the attachments of different applications from each other in the
         * cloud storage and to enable deleting all of them at once.
         */
        fun prefix(applicationId: Long): String = "$applicationId/"
    }
}
