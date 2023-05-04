package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator.validate
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime.now
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentService(
    val hankeRepository: HankeRepository,
    val attachmentRepository: HankeAttachmentRepository,
) {

    @Transactional(readOnly = true)
    fun getMetadataList(hankeTunnus: String): List<HankeAttachmentMetadata> =
        findHanke(hankeTunnus).liitteet.map { it.toMetadata() }

    @Transactional(readOnly = true)
    fun getContent(hankeTunnus: String, attachmentId: UUID): Pair<String, ByteArray> {
        val attachment = findHanke(hankeTunnus).liitteet.findBy(attachmentId)

        if (attachment.scanStatus != OK) {
            throw AttachmentNotFoundException(attachmentId)
        }

        return Pair(attachment.fileName, attachment.content)
    }

    @Transactional
    fun addAttachment(hankeTunnus: String, attachment: MultipartFile): HankeAttachmentMetadata {
        val file = validate(attachment)
        val hanke = findHanke(hankeTunnus)
        val result =
            HankeAttachmentEntity(
                id = null,
                fileName = file.originalFilename!!,
                content = file.bytes,
                createdAt = now(),
                createdByUserId = currentUserId(),
                scanStatus = OK,
                hanke = hanke,
            )

        return attachmentRepository.save(result).toMetadata().also {
            logger.info { "Added attachment ${it.id} to hanke $hankeTunnus" }
        }
    }

    @Transactional
    fun deleteAttachment(hankeTunnus: String, attachmentId: UUID) {
        val attachmentToDelete = findHanke(hankeTunnus).liitteet.findBy(attachmentId)
        attachmentRepository.deleteAttachment(attachmentToDelete.id!!)
        logger.info { "Deleted hanke attachment ${attachmentToDelete.id}" }
    }

    private fun findHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

    private fun List<HankeAttachmentEntity>.findBy(attachmentId: UUID): HankeAttachmentEntity =
        find { it.id == attachmentId } ?: throw AttachmentNotFoundException(attachmentId)
}
