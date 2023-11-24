package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentContentService(
    private val hankeAttachmentContentRepository: HankeAttachmentContentRepository,
    private val fileClient: FileClient,
) {

    fun save(attachmentId: UUID, content: ByteArray) {
        hankeAttachmentContentRepository.save(HankeAttachmentContentEntity(attachmentId, content))
    }

    /** Move the attachment content to cloud. In test-data use for now, can be used for HAI-1964. */
    @Transactional
    fun moveToCloud(attachment: HankeAttachmentEntity): String {
        val path = attachment.cloudPath()
        val content =
            hankeAttachmentContentRepository.findByIdOrNull(attachment.id!!)
                ?: run {
                    logger.error { "Content not found for hanke attachment ${attachment.id}" }
                    throw AttachmentNotFoundException(attachment.id!!)
                }
        fileClient.upload(
            Container.HANKE_LIITTEET,
            path,
            attachment.fileName,
            MediaType.parseMediaType(attachment.contentType),
            content.content,
        )
        hankeAttachmentContentRepository.delete(content)
        attachment.blobLocation = path
        return path
    }

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

    fun delete(attachment: HankeAttachmentEntity) {
        logger.info { "Deleting attachment content from hanke attachment ${attachment.id}..." }
        attachment.blobLocation?.let { fileClient.delete(Container.HANKE_LIITTEET, it) }
    }

    fun deleteAllForHanke(hankeId: Int) {
        fileClient.deleteAllByPrefix(Container.HANKE_LIITTEET, hankePrefix(hankeId))
    }

    fun find(attachment: HankeAttachmentEntity): ByteArray =
        attachment.blobLocation?.let { readFromFile(it, attachment.id!!) }
            ?: readFromDatabase(attachment.id!!)

    fun readFromFile(location: String, attachmentId: UUID): ByteArray =
        try {
            fileClient.download(Container.HANKE_LIITTEET, location).content.toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachmentId)
        }

    fun readFromDatabase(attachmentId: UUID): ByteArray =
        hankeAttachmentContentRepository.findByIdOrNull(attachmentId)?.content
            ?: run {
                logger.error { "Content not found for hanke attachment $attachmentId" }
                throw AttachmentNotFoundException(attachmentId)
            }

    companion object {
        fun generateBlobPath(hankeId: Int) =
            "${hankePrefix(hankeId)}${UUID.randomUUID()}"
                .also { logger.info { "Generated blob path: $it" } }

        /** Name (path from container root) the attachment should have in the cloud storage. */
        fun HankeAttachmentEntity.cloudPath(): String = hankePrefix(hanke.id) + id!!.toString()

        /**
         * Prefix derived from hanke each attachment of that hanke should have in the cloud storage.
         *
         * Used to distinguish the attachments of different from each other in the cloud storage and
         * to enable deleting all of them at once.
         */
        private fun hankePrefix(hankeId: Int): String = "$hankeId/"
    }
}
