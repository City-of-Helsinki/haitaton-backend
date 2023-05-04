package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.FileScanService
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime.now
import java.util.UUID
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentService(
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val scanService: FileScanService,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        findApplication(applicationId).attachments.map { it.toMetadata() }

    @Transactional(readOnly = true)
    fun getContent(applicationId: Long, attachmentId: UUID): Pair<String, ByteArray> {
        val attachment = findApplication(applicationId).attachments.findBy(attachmentId)

        with(attachment) {
            if (scanStatus != OK) {
                logger.warn { "Attachment $id with scan status: $scanStatus cannot be viewed." }
                throw AttachmentNotFoundException(attachmentId)
            }

            return Pair(fileName, content)
        }
    }

    @Transactional
    fun addAttachment(
        applicationId: Long,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile
    ): ApplicationAttachmentMetadata {
        val application = findApplication(applicationId)
        val file = scanService.validate(attachment)

        val applicationAttachment =
            ApplicationAttachmentEntity(
                id = null,
                fileName = file.originalFilename!!,
                content = file.bytes,
                createdByUserId = currentUserId(),
                createdAt = now(),
                scanStatus = OK,
                attachmentType = attachmentType,
                application = application,
            )

        return attachmentRepository.save(applicationAttachment).toMetadata().also {
            logger.info { "Added attachment ${it.id} to application $applicationId" }
        }
    }

    @Transactional
    fun deleteAttachment(applicationId: Long, attachmentId: UUID) {
        val attachment = findApplication(applicationId).attachments.findBy(attachmentId)
        attachmentRepository.deleteAttachment(attachment.id!!)
        logger.info { "Deleted hanke attachment ${attachment.id}" }
    }

    private fun findApplication(applicationId: Long): ApplicationEntity =
        applicationRepository.findById(applicationId).orElseThrow {
            ApplicationNotFoundException(applicationId)
        }

    private fun List<ApplicationAttachmentEntity>.findBy(
        attachmentId: UUID
    ): ApplicationAttachmentEntity =
        find { it.id == attachmentId } ?: throw AttachmentNotFoundException(attachmentId)
}
