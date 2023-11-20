package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/** Needed for attachment services transaction management. See usages for reasoning. */
@Component
class AttachmentPersister(
    private val hankeRepository: HankeRepository,
    private val hankeAttachmentRepository: HankeAttachmentRepository
) {
    @Transactional
    fun hankeAttachment(
        filename: String,
        mediaType: String,
        blobPath: String,
        hankeTunnus: String,
    ): HankeAttachmentEntity =
        hankeAttachmentRepository.save(
            HankeAttachmentEntity(
                id = null,
                fileName = filename,
                contentType = mediaType,
                createdAt = OffsetDateTime.now(),
                createdByUserId = currentUserId(),
                blobLocation = blobPath,
                hanke = getHanke(hankeTunnus),
            )
        )

    fun checkRoomForAttachment(hankeId: Int, hankeTunnus: String) =
        verifyCount(hankeAttachmentRepository.countByHankeId(hankeId))

    private fun getHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus)?.also {
            checkRoomForAttachment(it.id, it.hankeTunnus)
        } ?: throw HankeNotFoundException(hankeTunnus)

    private fun verifyCount(count: Int) {
        logger.info { "There is $count attachments beforehand." }
        if (count >= ALLOWED_ATTACHMENT_COUNT) {
            throw AttachmentInvalidException("Attachment limit reached")
        }
    }
}
