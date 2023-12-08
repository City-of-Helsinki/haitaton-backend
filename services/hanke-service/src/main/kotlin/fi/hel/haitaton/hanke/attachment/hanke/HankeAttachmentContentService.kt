package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentContentService(
    private val fileClient: FileClient,
) {
    /** Uploads and returns the location of the created blob. */
    fun upload(fileName: String, contentType: MediaType, content: ByteArray, hankeId: Int): String {
        val blobPath = generateBlobPath(hankeId)
        fileClient.upload(
            container = Container.HANKE_LIITTEET,
            path = blobPath,
            originalFilename = fileName,
            contentType = contentType,
            content = content,
        )
        return blobPath
    }

    fun delete(attachment: HankeAttachmentMetadata) {
        logger.info { "Deleting attachment content from hanke attachment ${attachment.id}..." }
        fileClient.delete(Container.HANKE_LIITTEET, attachment.blobLocation)
    }

    fun deleteAllForHanke(hankeId: Int) {
        fileClient.deleteAllByPrefix(Container.HANKE_LIITTEET, prefix(hankeId))
    }

    fun find(attachment: HankeAttachmentMetadata): ByteArray =
        try {
            fileClient.download(Container.HANKE_LIITTEET, attachment.blobLocation).content.toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachment.id)
        }

    companion object {
        fun generateBlobPath(hankeId: Int) =
            "${prefix(hankeId)}${UUID.randomUUID()}"
                .also { logger.info { "Generated blob path: $it" } }

        /**
         * Prefix derived from hanke each attachment of that hanke should have in the cloud storage.
         *
         * Used to distinguish the attachments of different from each other in the cloud storage and
         * to enable deleting all of them at once.
         */
        private fun prefix(hankeId: Int): String = "$hankeId/"
    }
}
