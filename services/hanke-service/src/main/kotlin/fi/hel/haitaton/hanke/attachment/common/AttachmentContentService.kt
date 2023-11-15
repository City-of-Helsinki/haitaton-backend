package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.attachment.azure.Container
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AttachmentContentService(
    private val applicationAttachmentContentRepository: ApplicationAttachmentContentRepository,
    private val hankeAttachmentContentRepository: HankeAttachmentContentRepository,
    private val fileClient: FileClient,
) {

    fun saveApplicationContent(attachmentId: UUID, content: ByteArray) {
        applicationAttachmentContentRepository.save(
            ApplicationAttachmentContentEntity(attachmentId, content)
        )
    }

    fun findApplicationContent(attachmentId: UUID): ByteArray =
        applicationAttachmentContentRepository
            .findById(attachmentId)
            .map { it.content }
            .orElseThrow {
                logger.error { "Content not found for application attachment $attachmentId" }
                AttachmentNotFoundException(attachmentId)
            }

    fun saveHankeContent(attachmentId: UUID, content: ByteArray) {
        hankeAttachmentContentRepository.save(HankeAttachmentContentEntity(attachmentId, content))
    }

    fun findHankeContent(attachment: HankeAttachmentEntity): ByteArray =
        attachment.blobLocation?.let { readHankeAttachmentFromFile(it, attachment.id!!) }
            ?: readHankeAttachmentFromDatabase(attachment.id!!)

    fun readHankeAttachmentFromFile(location: String, attachmentId: UUID): ByteArray =
        try {
            fileClient.download(Container.HANKE_LIITTEET, location).content.toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachmentId)
        }

    fun readHankeAttachmentFromDatabase(attachmentId: UUID): ByteArray =
        hankeAttachmentContentRepository.findByIdOrNull(attachmentId)?.content
            ?: run {
                logger.error { "Content not found for hanke attachment $attachmentId" }
                throw AttachmentNotFoundException(attachmentId)
            }
}
