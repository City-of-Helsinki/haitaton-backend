package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.domain.Loggable
import mu.KotlinLogging
import org.springframework.http.MediaType

private val logger = KotlinLogging.logger {}

interface AttachmentService<E : Loggable, M : AttachmentMetadata> {

    fun upload(filename: String, contentType: MediaType, content: ByteArray, entity: E): String

    fun createMetadata(
        filename: String,
        contentType: String,
        size: Long,
        blobPath: String,
        entity: E,
        attachmentType: ApplicationAttachmentType? = null,
    ): M

    fun delete(blobPath: String): Boolean

    /**
     * Saves the attachment.
     *
     * It first tries to upload the attachment. If the upload fails, an exception is thrown.
     *
     * Then it saves the metadata to DB, including the path the file was uploaded to. If the DB-save
     * fails for some reason, the uploaded file is deleted.
     *
     * If the deletion fails as well, we log the errors and return an error. The file will be
     * deleted when the hanke / hakemus is deleted and all files with the same prefix are removed.
     */
    fun saveAttachment(
        entity: E,
        content: ByteArray,
        filename: String,
        contentType: MediaType,
        attachmentType: ApplicationAttachmentType? = null,
    ): M {
        logger.info { "Saving attachment content for ${entity.logString()}" }
        val blobPath = upload(filename, contentType, content, entity)
        logger.info { "Saving attachment metadata for ${entity.logString()}" }
        val newAttachment =
            try {
                createMetadata(
                    filename,
                    contentType.toString(),
                    content.size.toLong(),
                    blobPath,
                    entity,
                    attachmentType,
                )
            } catch (e: Exception) {
                logger.error(e) {
                    "Attachment metadata save failed, deleting attachment content $blobPath"
                }
                delete(blobPath)
                throw e
            }
        logger.info {
            "Added attachment metadata ${newAttachment.id} and content $blobPath for ${entity.logString()}"
        }
        return newAttachment
    }
}
