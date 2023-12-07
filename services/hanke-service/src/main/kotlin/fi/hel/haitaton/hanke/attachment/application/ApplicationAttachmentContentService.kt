package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.Attachment
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentContentService(
    val contentRepository: ApplicationAttachmentContentRepository,
    val fileClient: FileClient,
) {
    fun save(attachmentId: UUID, content: ByteArray) {
        contentRepository.save(ApplicationAttachmentContentEntity(attachmentId, content))
    }

    fun find(attachment: Attachment): ByteArray =
        attachment.blobLocation?.let { readFromFile(it, attachment.id) }
            ?: readFromDatabase(attachment.id)

    fun readFromFile(location: String, attachmentId: UUID): ByteArray {
        return try {
            fileClient.download(Container.HAKEMUS_LIITTEET, location).content.toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachmentId)
        }
    }

    fun readFromDatabase(attachmentId: UUID): ByteArray =
        contentRepository.findByIdOrNull(attachmentId)?.content
            ?: run {
                logger.error { "Content not found for hakemus attachment $attachmentId" }
                throw AttachmentNotFoundException(attachmentId)
            }

    companion object {
        fun generateBlobPath(applicationId: Long) =
            "${applicationPrefix(applicationId)}${UUID.randomUUID()}"
                .also { logger.info { "Generated blob path: $it" } }

        /**
         * Prefix derived from application each attachment of that application should have in the
         * cloud storage.
         *
         * Used to distinguish the attachments of different applications from each other in the
         * cloud storage and to enable deleting all of them at once.
         */
        private fun applicationPrefix(applicationId: Long): String = "$applicationId/"
    }
}
