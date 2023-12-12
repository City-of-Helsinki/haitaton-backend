package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.ALLOWED_ATTACHMENT_COUNT
import fi.hel.haitaton.hanke.allu.ApplicationStatus
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
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
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
class ApplicationAttachmentService(
    private val cableReportService: CableReportService,
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        attachmentRepository.findByApplicationId(applicationId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = findAttachment(attachmentId)
        val content = attachmentContentService.find(attachment)

        return AttachmentContent(attachment.fileName, attachment.contentType, content)
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
        logger.info {
            "Adding attachment to application, applicationId = $applicationId, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }
        val filename = AttachmentValidator.validFilename(attachment.originalFilename)
        val application =
            findApplication(applicationId).also { application ->
                ensureApplicationIsPending(application)
                ensureRoomForAttachment(applicationId)
                ensureContentTypeIsSet(attachment.contentType)
                scanAttachment(filename, attachment.bytes)
            }

        val entity =
            ApplicationAttachmentEntity(
                id = null,
                fileName = filename,
                contentType = attachment.contentType!!,
                blobLocation = null,
                createdByUserId = currentUserId(),
                createdAt = now(),
                attachmentType = attachmentType,
                applicationId = application.id!!,
            )

        val newAttachment = attachmentRepository.save(entity)
        attachmentContentService.save(newAttachment.id!!, attachment.bytes)

        application.alluid?.let {
            cableReportService.addAttachment(it, newAttachment.toAlluAttachment(attachment.bytes))
        }

        return newAttachment.toDto().also {
            logger.info { "Added attachment ${it.id} to application $applicationId" }
        }
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    @Transactional
    fun deleteAttachment(attachmentId: UUID) {
        val attachment = findAttachment(attachmentId)
        val application = findApplication(attachment.applicationId)

        if (isInAllu(application)) {
            logger.warn {
                "Application ${application.id} is in Allu, attachments cannot be deleted."
            }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        attachmentRepository.deleteById(attachment.id!!)
        logger.info { "Deleted application attachment ${attachment.id}" }
    }

    @Transactional(readOnly = true)
    fun sendInitialAttachments(alluId: Int, applicationId: Long) {
        logger.info { "Sending initial attachments for application, alluid=$alluId" }
        val attachments = attachmentRepository.findByApplicationId(applicationId)
        if (attachments.isEmpty()) {
            logger.info { "No attachments to send for alluId $alluId" }
            return
        }

        cableReportService.addAttachments(alluId, attachments) { attachmentContentService.find(it) }
    }

    private fun findAttachment(attachmentId: UUID): ApplicationAttachmentEntity =
        attachmentRepository.findByIdOrNull(attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun findApplication(applicationId: Long): ApplicationEntity =
        applicationRepository.findById(applicationId).orElseThrow {
            ApplicationNotFoundException(applicationId)
        }

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }

    private fun ensureApplicationIsPending(application: ApplicationEntity) {
        if (!isPending(application.alluid, application.alluStatus)) {
            logger.warn { "Application is processing, cannot add attachment." }
            throw ApplicationAlreadyProcessingException(application.id, application.alluid)
        }
    }

    private fun ensureRoomForAttachment(applicationId: Long) {
        if (attachmentAmountReached(applicationId)) {
            logger.warn {
                "Application $applicationId has reached the allowed amount of attachments."
            }
            throw AttachmentLimitReachedException(applicationId, ALLOWED_ATTACHMENT_COUNT)
        }
    }

    private fun ensureContentTypeIsSet(contentType: String?) {
        contentType ?: throw AttachmentInvalidException("Content-type was not set")
    }

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

    private fun isInAllu(application: ApplicationEntity): Boolean = application.alluid != null

    private fun attachmentAmountReached(applicationId: Long): Boolean {
        val attachmentCount = attachmentRepository.countByApplicationId(applicationId)
        logger.info {
            "Application $applicationId contains $attachmentCount attachments beforehand."
        }
        return attachmentCount >= ALLOWED_ATTACHMENT_COUNT
    }
}

class ApplicationInAlluException(id: Long?, alluId: Int?) :
    RuntimeException("Application is already sent to Allu, applicationId=$id, alluId=$alluId")
