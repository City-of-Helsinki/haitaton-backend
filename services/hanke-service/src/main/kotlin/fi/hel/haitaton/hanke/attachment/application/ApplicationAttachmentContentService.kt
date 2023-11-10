package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentContentService(
    private val applicationAttachmentContentRepository: ApplicationAttachmentContentRepository
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
}
