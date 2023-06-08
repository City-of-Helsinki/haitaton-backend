package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus.OK
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
    private val cableReportService: CableReportService,
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val scanClient: FileScanClient,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        findApplication(applicationId).attachments.map { it.toMetadata() }

    @Transactional(readOnly = true)
    fun getContent(applicationId: Long, attachmentId: UUID): AttachmentContent {
        val attachment = findApplication(applicationId).attachments.findOrThrow(attachmentId)

        with(attachment) {
            if (scanStatus != OK) {
                logger.warn { "Attachment $id with scan status: $scanStatus cannot be viewed." }
                throw AttachmentNotFoundException(attachmentId)
            }

            return AttachmentContent(fileName, contentType, content)
        }
    }

    /**
     * Attachment can be added if application has not proceeded to HANDLING or later status. It will
     * be sent immediately if application is in Allu (alluId present).
     */
    @Transactional
    fun addAttachment(
        applicationId: Long,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile
    ): ApplicationAttachmentMetadata {
        val application = findApplication(applicationId)

        if (!isPending(application)) {
            logger.warn { "Application is processing, cannot add attachment." }
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }

        validateAttachment(attachment)

        val entity =
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

        val newAttachment = attachmentRepository.save(entity)

        application.alluid?.let { sendAttachment(it, newAttachment) } // no need to re-check status

        return newAttachment.toMetadata().also {
            logger.info { "Added attachment ${it.id} to application $applicationId" }
        }
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    @Transactional
    fun deleteAttachment(applicationId: Long, attachmentId: UUID) {
        val application = findApplication(applicationId)

        if (isInAllu(application)) {
            logger.warn { "Application $applicationId is in Allu, attachments cannot be deleted." }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        val attachment = application.attachments.findOrThrow(attachmentId)
        attachmentRepository.deleteAttachment(attachment.id!!)
        logger.info { "Deleted application attachment ${attachment.id}" }
    }

    fun sendInitialAttachments(alluId: Int, application: ApplicationEntity) {
        logger.info { "Sending initial attachments for application, alluid=$alluId" }
        val attachments = application.attachments.map { it.toAlluAttachment() }
        if (attachments.isEmpty()) {
            logger.info { "No attachments to send for alluId $alluId" }
            return
        }

        cableReportService.addAttachments(alluId, attachments)
    }

    private fun findApplication(applicationId: Long): ApplicationEntity =
        applicationRepository.findById(applicationId).orElseThrow {
            ApplicationNotFoundException(applicationId)
        }

    private fun List<ApplicationAttachmentEntity>.findOrThrow(
        attachmentId: UUID
    ): ApplicationAttachmentEntity =
        find { it.id == attachmentId } ?: throw AttachmentNotFoundException(attachmentId)

    private fun validateAttachment(attachment: MultipartFile) =
        with(attachment) {
            AttachmentValidator.validate(this)
            val scanResult = scanClient.scan(listOf(FileScanInput(originalFilename!!, bytes)))
            if (scanResult.hasInfected()) {
                throw AttachmentInvalidException("Infected file detected, see previous logs.")
            }
        }

    /** Application considered pending if no alluId or status null, pending, or pending_client. */
    private fun isPending(application: ApplicationEntity): Boolean {
        val alluId = application.alluid
        alluId ?: return true
        return when (application.alluStatus) {
            null,
            PENDING,
            PENDING_CLIENT -> alluPending(alluId)
            else -> false
        }
    }

    /** Check current status from Allu. */
    private fun alluPending(alluId: Int): Boolean {
        val status = cableReportService.getApplicationInformation(alluId).status
        return listOf(PENDING, PENDING_CLIENT).contains(status)
    }

    private fun isInAllu(application: ApplicationEntity): Boolean = application.alluid != null

    /** Attachment should be sent if application is in Allu. Must check status before sending. */
    private fun sendAttachment(alluId: Int, attachment: ApplicationAttachmentEntity) {
        cableReportService.addAttachment(alluId, attachment.toAlluAttachment())
    }
}

class ApplicationInAlluException(id: Long?, alluId: Int?) :
    RuntimeException("Application is already sent to Allu, applicationId=$id, alluId=$alluId")
