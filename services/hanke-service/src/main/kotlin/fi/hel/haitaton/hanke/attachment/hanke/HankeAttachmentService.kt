package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime.now
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentService(
    val hankeRepository: HankeRepository,
    val attachmentRepository: HankeAttachmentRepository,
    val attachmentContentService: HankeAttachmentContentService,
    val scanClient: FileScanClient,
) {

    @Transactional(readOnly = true)
    fun getMetadataList(hankeTunnus: String): List<HankeAttachmentMetadata> =
        findHanke(hankeTunnus).liitteet.map { it.toMetadata() }

    @Transactional(readOnly = true)
    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = findAttachment(attachmentId)

        val content = attachmentContentService.find(attachment)
        return AttachmentContent(attachment.fileName, attachment.contentType, content)
    }

    @Transactional
    fun addAttachment(hankeTunnus: String, attachment: MultipartFile): HankeAttachmentMetadata {
        logger.info {
            "Adding attachment to hanke, hankeTunnus = $hankeTunnus, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }
        val filename = AttachmentValidator.validFilename(attachment.originalFilename)
        val hanke =
            findHanke(hankeTunnus).also { hanke ->
                ensureRoomForAttachment(hanke.id)
                scanAttachment(filename, attachment.bytes)
            }

        val entity =
            HankeAttachmentEntity(
                id = null,
                fileName = filename,
                contentType = attachment.contentType!!,
                createdAt = now(),
                createdByUserId = currentUserId(),
                blobLocation = null, // null until blobs are implemented
                hanke = hanke,
            )
        val savedAttachment = attachmentRepository.save(entity)
        attachmentContentService.save(savedAttachment.id!!, attachment.bytes)

        return savedAttachment.toMetadata().also {
            logger.info { "Added attachment ${it.id} to hanke $hankeTunnus" }
        }
    }

    /** Move the attachment content to cloud. In test-data use for now, can be used for HAI-1964. */
    @Transactional
    fun moveToCloud(attachmentId: UUID): String {
        logger.info { "Moving attachment content to cloud for hanke attachment $attachmentId" }
        val attachment = findAttachment(attachmentId)
        return attachmentContentService.moveToCloud(attachment)
    }

    @Transactional
    fun deleteAttachment(attachmentId: UUID) {
        logger.info { "Deleting hanke attachment $attachmentId..." }
        val attachmentToDelete = findAttachment(attachmentId)
        attachmentContentService.delete(attachmentToDelete)
        attachmentToDelete.hanke.liitteet.remove(attachmentToDelete)
    }

    private fun findAttachment(attachmentId: UUID) =
        attachmentRepository.findByIdOrNull(attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun findHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

    private fun ensureRoomForAttachment(hankeId: Int) {
        if (attachmentAmountReached(hankeId)) {
            logger.warn { "Hanke $hankeId has reached the allowed amount of attachments." }
            throw AttachmentInvalidException("Attachment amount limit reached")
        }
    }

    private fun attachmentAmountReached(hankeId: Int): Boolean {
        val attachmentCount = attachmentRepository.countByHankeId(hankeId)
        logger.info { "Application $hankeId contains $attachmentCount attachments beforehand." }
        return attachmentCount >= ALLOWED_ATTACHMENT_COUNT
    }

    fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }
}
