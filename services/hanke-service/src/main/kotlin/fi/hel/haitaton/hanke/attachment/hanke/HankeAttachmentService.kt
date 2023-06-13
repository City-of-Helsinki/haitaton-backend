package fi.hel.haitaton.hanke.attachment.hanke

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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentService(
    val hankeRepository: HankeRepository,
    val attachmentRepository: HankeAttachmentRepository,
    val scanClient: FileScanClient,
) {

    @Transactional(readOnly = true)
    fun getMetadataList(hankeTunnus: String): List<HankeAttachmentMetadata> =
        findHanke(hankeTunnus).liitteet.map { it.toMetadata() }

    @Transactional(readOnly = true)
    fun getContent(hankeTunnus: String, attachmentId: UUID): AttachmentContent {
        val attachment = findHanke(hankeTunnus).liitteet.findBy(attachmentId)

        with(attachment) {
            return AttachmentContent(fileName, contentType, content)
        }
    }

    @Transactional
    fun addAttachment(hankeTunnus: String, attachment: MultipartFile): HankeAttachmentMetadata {
        val hanke = findHanke(hankeTunnus)
        validateAttachment(attachment)

        val result =
            HankeAttachmentEntity(
                id = null,
                fileName = attachment.originalFilename!!,
                content = attachment.bytes,
                contentType = attachment.contentType!!,
                createdAt = now(),
                createdByUserId = currentUserId(),
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

    private fun validateAttachment(attachment: MultipartFile) =
        with(attachment) {
            AttachmentValidator.validate(this)
            val scanResult = scanClient.scan(listOf(FileScanInput(originalFilename!!, bytes)))
            if (scanResult.hasInfected()) {
                throw AttachmentInvalidException("Infected file detected, see previous logs.")
            }
        }
}
