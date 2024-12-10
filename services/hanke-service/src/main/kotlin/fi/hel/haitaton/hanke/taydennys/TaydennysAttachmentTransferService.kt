package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TaydennysAttachmentTransferService(
    private val taydennysAttachmentRepository: TaydennysAttachmentRepository,
    private val hakemusAttachmentRepository: ApplicationAttachmentRepository,
) {

    /** Transfers täydennys attachment to hakemus. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun transferAttachmentToHakemus(
        attachment: TaydennysAttachmentMetadata,
        hakemusEntity: HakemusEntity,
    ) {
        val hakemusAttachmentEntity =
            ApplicationAttachmentEntity(
                id = null,
                fileName = attachment.fileName,
                contentType = attachment.contentType,
                size = attachment.size,
                createdByUserId = attachment.createdByUserId,
                createdAt = attachment.createdAt,
                blobLocation = attachment.blobLocation,
                applicationId = hakemusEntity.id,
                attachmentType = attachment.attachmentType,
            )
        hakemusAttachmentRepository.save(hakemusAttachmentEntity)
        taydennysAttachmentRepository.deleteById(attachment.id)
    }
}
