package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.Attachment as AlluAttachment
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentService(
    private val cableReportService: CableReportService,
    private val metadataService: ApplicationAttachmentMetadataService,
    private val applicationRepository: ApplicationRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) {
    /** Authorization in controller throws exception if application ID is unknown. */
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadataDto> =
        metadataService.getMetadataList(applicationId)

    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = metadataService.findAttachment(attachmentId)
        val content = attachmentContentService.find(attachment)

        return AttachmentContent(attachment.fileName, attachment.contentType, content)
    }

    /**
     * Attachment can be added if application has not proceeded to HANDLING or later status. It will
     * be sent immediately if application is in Allu (alluId present).
     */
    fun addAttachment(
        applicationId: Long,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile
    ): ApplicationAttachmentMetadataDto {
        logger.info {
            "Adding attachment to application, applicationId = $applicationId, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }
        val filename = AttachmentValidator.validFilename(attachment.originalFilename)
        val application = findApplication(applicationId)

        ensureApplicationIsPending(application)
        val contentType = ensureContentTypeIsSet(attachment.contentType)
        scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(applicationId)

        val newAttachment =
            metadataService.create(filename, contentType, attachmentType, applicationId)
        attachmentContentService.save(newAttachment.id, attachment.bytes)

        try {
            application.alluid?.let {
                val alluAttachment = AlluAttachment(contentType, filename, attachment.bytes)
                cableReportService.addAttachment(it, alluAttachment)
            }
        } catch (e: Exception) {
            logger.error(e) { "Allu upload failed, deleting attachment from DB." }
            // No Blob upload yet, content will be deleted by SQL cascade
            metadataService.deleteAttachmentById(newAttachment.id)
            throw e
        }

        logger.info { "Added attachment ${newAttachment.id} to application $applicationId" }
        return newAttachment.toDto()
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    fun deleteAttachment(attachmentId: UUID) {
        val attachment = metadataService.findAttachment(attachmentId)
        val application = findApplication(attachment.applicationId)

        if (isInAllu(application)) {
            logger.warn {
                "Application ${application.id} is in Allu, attachments cannot be deleted."
            }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        attachmentContentService.delete(attachment)
        metadataService.deleteAttachmentById(attachment.id)

        logger.info { "Deleted application attachment ${attachment.id}" }
    }

    fun sendInitialAttachments(alluId: Int, applicationId: Long) {
        logger.info { "Sending initial attachments for application, alluid=$alluId" }
        val attachments = metadataService.findByApplicationId(applicationId)
        if (attachments.isEmpty()) {
            logger.info { "No attachments to send for alluId $alluId" }
            return
        }

        cableReportService.addAttachments(alluId, attachments) { attachmentContentService.find(it) }
    }

    private fun findApplication(applicationId: Long): Application =
        applicationRepository.findByIdOrNull(applicationId)?.toApplication()
            ?: throw ApplicationNotFoundException(applicationId)

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }

    private fun ensureApplicationIsPending(application: Application) {
        if (!isPending(application.alluid, application.alluStatus)) {
            logger.warn { "Application is processing, cannot add attachment." }
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }
    }

    private fun ensureContentTypeIsSet(contentType: String?): String =
        contentType ?: throw AttachmentInvalidException("Content-type was not set")

    /** Application considered pending if no alluId or status null, pending, or pending_client. */
    private fun isPending(alluId: Int?, alluStatus: ApplicationStatus?): Boolean {
        alluId ?: return true
        return when (alluStatus) {
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

    private fun isInAllu(application: Application): Boolean = application.alluid != null
    fun deleteAllByApplicationId(applicationId: Long?) {
        if (applicationId == null) {
            logger.info { "Application ID is null, cannot delete attachments." }
            return
        }
        logger.info { "Deleting all attachments for application $applicationId" }
        metadataService.findByApplicationId(applicationId).forEach {
            deleteAttachment(it.id)
        }
    }
}
