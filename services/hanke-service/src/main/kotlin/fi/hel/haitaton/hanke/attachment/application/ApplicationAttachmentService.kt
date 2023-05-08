package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationArgumentException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
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
class ApplicationAttachmentService(
    private val applicationService: ApplicationService,
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val scanClient: FileScanClient,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        findApplication(applicationId).attachments.map { it.toMetadata() }

    @Transactional(readOnly = true)
    fun getContent(applicationId: Long, attachmentId: UUID): AttachmentContent {
        val attachment = findApplication(applicationId).attachments.findBy(attachmentId)

        with(attachment) {
            if (scanStatus != OK) {
                logger.warn { "Attachment $id with scan status: $scanStatus cannot be viewed." }
                throw AttachmentNotFoundException(attachmentId)
            }

            return AttachmentContent(fileName, contentType, content)
        }
    }

    @Transactional
    fun addAttachment(
        applicationId: Long,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile
    ): ApplicationAttachmentMetadata {
        val application = findApplication(applicationId)

        validateAttachment(attachment)

        val applicationAttachment =
            ApplicationAttachmentEntity(
                id = null,
                fileName = attachment.originalFilename!!,
                content = attachment.bytes,
                contentType = attachment.contentType!!,
                createdByUserId = currentUserId(),
                createdAt = now(),
                scanStatus = OK,
                attachmentType = attachmentType,
                application = application,
            )

        val savedAttachment = attachmentRepository.save(applicationAttachment)

        sendIfPending(application.toApplication(), savedAttachment)

        return savedAttachment.toMetadata().also {
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

    private fun validateAttachment(attachment: MultipartFile) {
        AttachmentValidator.validate(attachment)
        val scanResult =
            scanClient.scan(listOf(FileScanInput(attachment.originalFilename!!, attachment.bytes)))
        if (scanResult.hasInfected()) {
            throw AttachmentUploadException("Infected file detected, see previous logs.")
        }
    }

    /**
     * Attachment should be sent if application is in Allu but has not yet proceeded to handling
     * (status PENDING). If PENDING, application is in Allu and alluId should exist.
     */
    private fun sendIfPending(application: Application, attachment: ApplicationAttachmentEntity) =
        with(application) {
            logger.info {
                "Check application if should send attachment, alluId: '$alluid', alluStatus: '$alluStatus'"
            }
            if (alluStatus == PENDING) {
                val id = alluid ?: throw ApplicationArgumentException("AlluId null")
                applicationService.sendAttachment(id, attachment.toAlluAttachment())
            }
        }
}
