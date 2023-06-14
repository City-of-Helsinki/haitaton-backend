package fi.hel.haitaton.hanke.attachment.common

import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AttachmentContentService(
    private val applicationAttachmentContentRepository: ApplicationAttachmentContentRepository,
    private val hankeAttachmentContentRepository: HankeAttachmentContentRepository
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

    fun findHankeContent(attachmentId: UUID): ByteArray =
        hankeAttachmentContentRepository
            .findById(attachmentId)
            .map { it.content }
            .orElseThrow {
                logger.error { "Content not found for hanke attachment $attachmentId" }
                AttachmentNotFoundException(attachmentId)
            }
}
