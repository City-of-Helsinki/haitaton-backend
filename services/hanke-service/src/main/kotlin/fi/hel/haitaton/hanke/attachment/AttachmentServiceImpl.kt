package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

class AttachmentServiceImpl(
    val hankeRepository: HankeRepository,
    val hankeAttachmentRepository: HankeAttachmentRepository,
) : AttachmentService {

    @Transactional
    override fun add(hankeTunnus: String, liite: MultipartFile): AttachmentMetadata {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw AttachmentUploadException("Hanke not found")

        val validLiite = AttachmentValidator.validate(liite)

        val attachment =
            HankeAttachmentEntity(
                id = null,
                fileName = validLiite.originalFilename!!,
                content = validLiite.bytes,
                createdAt = OffsetDateTime.now(),
                createdByUserId = currentUserId(),
                scanStatus = AttachmentScanStatus.OK,
                hanke = hanke,
            )

        return hankeAttachmentRepository.save(attachment).toMetadata()
    }

    @Transactional
    override fun removeAttachment(id: UUID) {
        hankeAttachmentRepository.deleteById(id)
        hankeAttachmentRepository.flush()
    }

    override fun getHankeAttachments(hankeTunnus: String): List<AttachmentMetadata> {
        val hanke =
            hankeRepository.findByHankeTunnus(hankeTunnus)
                ?: throw HankeNotFoundException(hankeTunnus)
        return hanke.liitteet.map { it.toMetadata() }
    }

    override fun getMetadata(id: UUID): AttachmentMetadata = findAttachment(id).toMetadata()

    override fun getContent(id: UUID): ByteArray {
        val attachment = findAttachment(id)

        if (attachment.scanStatus == AttachmentScanStatus.OK) {
            return attachment.content
        } else {
            throw AttachmentNotFoundException()
        }
    }

    private fun findAttachment(id: UUID) =
        hankeAttachmentRepository.findById(id).orElseThrow { AttachmentNotFoundException() }
}
