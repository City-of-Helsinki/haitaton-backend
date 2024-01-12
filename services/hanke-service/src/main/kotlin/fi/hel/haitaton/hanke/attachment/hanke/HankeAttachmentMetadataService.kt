package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentMetadataService(
    private val hankeRepository: HankeRepository,
    private val attachmentRepository: HankeAttachmentRepository,
) {

    @Transactional(readOnly = true)
    fun getMetadataList(hankeTunnus: String): List<HankeAttachmentMetadataDto> =
        findHanke(hankeTunnus).liitteet.map { it.toDto() }

    @Transactional(readOnly = true)
    fun findAttachment(attachmentId: UUID) = findAttachmentEntity(attachmentId).toDomain()

    @Transactional
    fun saveAttachment(
        hankeTunnus: String,
        name: String,
        type: String,
        size: Long,
        blobPath: String,
    ): HankeAttachmentMetadataDto {
        val hanke = findHanke(hankeTunnus).also { checkRoomForAttachment(it.id) }

        return attachmentRepository
            .save(
                HankeAttachmentEntity(
                    id = null,
                    fileName = name,
                    contentType = type,
                    size = size,
                    createdAt = OffsetDateTime.now(),
                    createdByUserId = currentUserId(),
                    blobLocation = blobPath,
                    hanke = hanke,
                )
            )
            .toDto()
    }

    @Transactional
    fun delete(attachmentId: UUID) {
        val attachmentToDelete = findAttachmentEntity(attachmentId)
        attachmentToDelete.hanke.liitteet.remove(attachmentToDelete)
    }

    @Transactional
    fun deleteAllByHanke(hankeId: Int) {
        val hankeEntity = hankeRepository.findByIdOrNull(hankeId)
        hankeEntity?.liitteet?.clear()
    }

    @Transactional(readOnly = true)
    fun hankeWithRoomForAttachment(hankeTunnus: String): HankeIdentifier =
        findHankeIdentifiers(hankeTunnus).also { checkRoomForAttachment(it.id) }

    private fun checkRoomForAttachment(hankeId: Int) =
        verifyCount(attachmentRepository.countByHankeId(hankeId))

    private fun verifyCount(count: Int) {
        logger.info { "There is $count attachments beforehand." }
        if (count >= ALLOWED_ATTACHMENT_COUNT) {
            throw AttachmentInvalidException("Attachment limit reached")
        }
    }

    private fun findAttachmentEntity(attachmentId: UUID): HankeAttachmentEntity =
        attachmentRepository.findByIdOrNull(attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun findHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

    private fun findHankeIdentifiers(hankeTunnus: String) =
        hankeRepository.findOneByHankeTunnus(hankeTunnus)
            ?: throw HankeNotFoundException(hankeTunnus)
}
