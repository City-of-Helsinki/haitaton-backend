package fi.hel.haitaton.hanke.attachment.taydennys

import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.FileClient
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class TaydennysAttachmentContentService(val fileClient: FileClient) {
    fun upload(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        taydennysId: UUID,
    ): String {
        val blobPath = generateBlobPath(taydennysId)
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

    companion object {
        fun generateBlobPath(taydennysId: UUID) =
            "${prefix(taydennysId)}${UUID.randomUUID()}"
                .also { logger.info { "Generated blob path: $it" } }

        /**
         * Prefix derived from täydennys each attachment of that täydennys should have in the cloud
         * storage.
         *
         * Used to distinguish the attachments of different täydennys from each other in the cloud
         * storage and to enable deleting all of them at once.
         */
        fun prefix(taydennysId: UUID): String = "$taydennysId/"
    }
}
