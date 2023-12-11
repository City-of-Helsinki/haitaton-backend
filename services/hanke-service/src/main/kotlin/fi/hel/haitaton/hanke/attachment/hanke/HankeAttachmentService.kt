package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachment
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
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
class HankeAttachmentService(
    private val hankeRepository: HankeRepository,
    private val attachmentRepository: HankeAttachmentRepository,
    private val attachmentContentService: HankeAttachmentContentService,
) {

    @Transactional(readOnly = true)
    fun getMetadataList(hankeTunnus: String): List<HankeAttachment> =
        findHanke(hankeTunnus).liitteet.map { it.toDomain() }

    @Transactional(readOnly = true)
    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = findAttachment(attachmentId)

        val content = attachmentContentService.find(attachment)
        return AttachmentContent(attachment.fileName, attachment.contentType, content)
    }

    @Transactional
    fun saveAttachment(
        hankeTunnus: String,
        name: String,
        type: String,
        blobPath: String,
    ): HankeAttachment {
        val hanke = findHanke(hankeTunnus).also { checkRoomForAttachment(it.id) }

        return attachmentRepository
            .save(
                HankeAttachmentEntity(
                    id = null,
                    fileName = name,
                    contentType = type,
                    createdAt = OffsetDateTime.now(),
                    createdByUserId = currentUserId(),
                    blobLocation = blobPath,
                    hanke = hanke,
                )
            )
            .toDomain()
    }

    @Transactional
    fun deleteAttachment(attachmentId: UUID) {
        logger.info { "Deleting hanke attachment $attachmentId..." }
        val attachmentToDelete = findAttachment(attachmentId)
        attachmentContentService.delete(attachmentToDelete)
        attachmentToDelete.hanke.liitteet.remove(attachmentToDelete)
    }

    @Transactional
    fun deleteAllAttachments(hanke: HankeIdentifier) {
        logger.info { "Deleting all attachments from hanke ${hanke.logString()}" }
        attachmentContentService.deleteAllForHanke(hanke.id)
        val hankeEntity = hankeRepository.findByIdOrNull(hanke.id)
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

    private fun findAttachment(attachmentId: UUID) =
        attachmentRepository.findByIdOrNull(attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun findHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

    private fun findHankeIdentifiers(hankeTunnus: String) =
        hankeRepository.findOneByHankeTunnus(hankeTunnus)
            ?: throw HankeNotFoundException(hankeTunnus)
}
