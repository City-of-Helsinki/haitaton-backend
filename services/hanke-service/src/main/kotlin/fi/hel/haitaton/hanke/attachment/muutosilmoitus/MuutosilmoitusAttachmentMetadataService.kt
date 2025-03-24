package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentMetadataService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusIdentifier
import java.time.OffsetDateTime
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class MuutosilmoitusAttachmentMetadataService(
    private val attachmentRepository: MuutosilmoitusAttachmentRepository,
    private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
    private val hakemusAttachmentService: ApplicationAttachmentMetadataService,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(muutosilmoitusId: UUID): List<MuutosilmoitusAttachmentMetadata> =
        attachmentRepository.findByMuutosilmoitusId(muutosilmoitusId).map { it.toDomain() }

    @Transactional(readOnly = true)
    fun findAttachment(attachmentId: UUID): MuutosilmoitusAttachmentMetadata =
        attachmentRepository.findByIdOrNull(attachmentId)?.toDomain()
            ?: throw AttachmentNotFoundException(attachmentId)

    @Transactional
    fun create(
        filename: String,
        contentType: String,
        size: Long,
        blobLocation: String,
        attachmentType: ApplicationAttachmentType,
        muutosilmoitusId: UUID,
    ): MuutosilmoitusAttachmentMetadata {
        val entity =
            MuutosilmoitusAttachmentEntity(
                id = null,
                fileName = filename,
                contentType = contentType,
                size = size,
                blobLocation = blobLocation,
                createdByUserId = currentUserId(),
                createdAt = OffsetDateTime.now(),
                attachmentType = attachmentType,
                muutosilmoitusId = muutosilmoitusId,
            )
        return attachmentRepository.save(entity).toDomain().also {
            logger.info {
                "Saved attachment metadata ${it.id} for muutosilmoitus $muutosilmoitusId"
            }
        }
    }

    @Transactional(readOnly = true)
    fun ensureRoomForAttachment(muutosilmoitus: MuutosilmoitusIdentifier) {
        if (attachmentAmountReached(muutosilmoitus)) {
            logger.warn {
                "Muutosilmoitus ${muutosilmoitus.id} has reached the allowed total amount of attachments for it and its hakemus."
            }
            throw AttachmentLimitReachedException(muutosilmoitus)
        }
    }

    @Transactional
    fun deleteAttachmentById(attachmentId: UUID) {
        attachmentRepository.deleteById(attachmentId)
        logger.info { "Deleted attachment metadata $attachmentId" }
    }

    /**
     * Delete all attachments for muutosilmoitus and return the blob locations of the deleted
     * attachments.
     */
    @Transactional
    fun deleteAllAttachments(muutosilmoitus: MuutosilmoitusIdentifier): List<String> {
        return attachmentRepository
            .deleteByMuutosilmoitusId(muutosilmoitus.id)
            .map(MuutosilmoitusAttachmentEntity::blobLocation)
            .also {
                logger.info {
                    "Deleted all attachment metadata for muutosilmoitus ${muutosilmoitus.logString()}"
                }
            }
    }

    /** Transfers all muutosilmoitus attachments to hakemus. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun transferAttachmentsToHakemus(
        muutosilmoitus: MuutosilmoitusIdentifier,
        hakemusEntity: HakemusEntity,
    ) {
        attachmentRepository
            .findByMuutosilmoitusId(muutosilmoitus.id)
            .map { it.toDomain() }
            .forEach { attachment ->
                hakemusAttachmentService.create(attachment, hakemusEntity.id)
                attachmentRepository.deleteById(attachment.id)
            }
    }

    private fun attachmentAmountReached(entity: MuutosilmoitusIdentifier): Boolean {
        val hakemusAttachmentCount =
            hakemusAttachmentRepository.countByApplicationId(entity.hakemusId)
        val muutosilmoitusAttachmentCount = attachmentRepository.countByMuutosilmoitusId(entity.id)
        val totalAttachmentCount = muutosilmoitusAttachmentCount + hakemusAttachmentCount
        logger.info {
            "Muutosilmoitus ${entity.id} and application ${entity.hakemusId} contain total of $totalAttachmentCount attachments beforehand."
        }
        return totalAttachmentCount >= ALLOWED_ATTACHMENT_COUNT
    }
}
