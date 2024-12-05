package fi.hel.haitaton.hanke.attachment.taydennys

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.taydennys.TaydennysIdentifier
import java.time.OffsetDateTime
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class TaydennysAttachmentMetadataService(
    private val attachmentRepository: TaydennysAttachmentRepository,
    private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(taydennysId: UUID): List<TaydennysAttachmentMetadata> =
        attachmentRepository.findByTaydennysId(taydennysId).map { it.toDomain() }

    @Transactional(readOnly = true)
    fun findAttachment(attachmentId: UUID): TaydennysAttachmentMetadata =
        attachmentRepository.findByIdOrNull(attachmentId)?.toDomain()
            ?: throw AttachmentNotFoundException(attachmentId)

    @Transactional
    fun create(
        filename: String,
        contentType: String,
        size: Long,
        blobLocation: String,
        attachmentType: ApplicationAttachmentType,
        taydennysId: UUID,
    ): TaydennysAttachmentMetadata {
        val entity =
            TaydennysAttachmentEntity(
                id = null,
                fileName = filename,
                contentType = contentType,
                size = size,
                blobLocation = blobLocation,
                createdByUserId = currentUserId(),
                createdAt = OffsetDateTime.now(),
                attachmentType = attachmentType,
                taydennysId = taydennysId,
            )
        return attachmentRepository.save(entity).toDomain().also {
            logger.info { "Saved attachment metadata ${it.id} for täydennys $taydennysId" }
        }
    }

    @Transactional(readOnly = true)
    fun ensureRoomForAttachment(taydennys: TaydennysIdentifier) {
        if (attachmentAmountReached(taydennys)) {
            logger.warn {
                "Täydennys ${taydennys.id} has reached the allowed total amount of attachments for it and its hakemus."
            }
            throw AttachmentLimitReachedException(taydennys.id, ALLOWED_ATTACHMENT_COUNT)
        }
    }

    private fun attachmentAmountReached(taydennys: TaydennysIdentifier): Boolean {
        val hakemusAttachmentCount =
            hakemusAttachmentRepository.countByApplicationId(taydennys.hakemusId())
        val taydennysAttachmentCount = attachmentRepository.countByTaydennysId(taydennys.id)
        val totalAttachmentCount = taydennysAttachmentCount + hakemusAttachmentCount
        logger.info {
            "Täydennys ${taydennys.id} and application ${taydennys.hakemusId()} contain total of $totalAttachmentCount attachments beforehand."
        }
        return totalAttachmentCount >= ALLOWED_ATTACHMENT_COUNT
    }

    /**
     * Delete all attachments for täydennys and return the blob locations of the deleted
     * attachments.
     */
    @Transactional
    fun deleteAllAttachments(taydennys: TaydennysIdentifier): List<String> {
        return attachmentRepository
            .deleteByTaydennysId(taydennys.id)
            .map(TaydennysAttachmentEntity::blobLocation)
            .also {
                logger.info {
                    "Deleted all attachment metadata for täydennys ${taydennys.logString()}"
                }
            }
    }
}
