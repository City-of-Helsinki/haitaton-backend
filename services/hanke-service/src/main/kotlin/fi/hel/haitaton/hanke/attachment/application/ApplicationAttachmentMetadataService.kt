package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentMetadataService(
    private val attachmentRepository: ApplicationAttachmentRepository,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadataDto> =
        attachmentRepository.findByApplicationId(applicationId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun findAttachment(attachmentId: UUID): ApplicationAttachmentMetadata =
        findAttachmentEntity(attachmentId).toDomain()

    @Transactional(readOnly = true)
    fun findByApplicationId(applicationId: Long): List<ApplicationAttachmentMetadata> =
        attachmentRepository.findByApplicationId(applicationId).map { it.toDomain() }

    @Transactional
    fun create(
        filename: String,
        contentType: String,
        attachmentType: ApplicationAttachmentType,
        applicationId: Long
    ): ApplicationAttachmentMetadata {
        val entity =
            ApplicationAttachmentEntity(
                id = null,
                fileName = filename,
                contentType = contentType,
                blobLocation = null,
                createdByUserId = currentUserId(),
                createdAt = OffsetDateTime.now(),
                attachmentType = attachmentType,
                applicationId = applicationId,
            )
        return attachmentRepository.save(entity).toDomain()
    }

    @Transactional
    fun deleteAttachmentById(attachmentId: UUID) {
        attachmentRepository.deleteById(attachmentId)
    }

    @Transactional
    fun deleteAllAttachments(id: Long) {
        attachmentRepository.deleteByApplicationId(id)
    }

    @Transactional(readOnly = true)
    fun ensureRoomForAttachment(applicationId: Long) {
        if (attachmentAmountReached(applicationId)) {
            logger.warn {
                "Application $applicationId has reached the allowed amount of attachments."
            }
            throw AttachmentLimitReachedException(applicationId, ALLOWED_ATTACHMENT_COUNT)
        }
    }

    private fun findAttachmentEntity(attachmentId: UUID): ApplicationAttachmentEntity =
        attachmentRepository.findByIdOrNull(attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun attachmentAmountReached(applicationId: Long): Boolean {
        val attachmentCount = attachmentRepository.countByApplicationId(applicationId)
        logger.info {
            "Application $applicationId contains $attachmentCount attachments beforehand."
        }
        return attachmentCount >= ALLOWED_ATTACHMENT_COUNT
    }
}

class ApplicationInAlluException(id: Long?, alluId: Int?) :
    RuntimeException("Application is already sent to Allu, applicationId=$id, alluId=$alluId")
