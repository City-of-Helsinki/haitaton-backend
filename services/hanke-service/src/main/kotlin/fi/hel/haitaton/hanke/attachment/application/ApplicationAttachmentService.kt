package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING
import fi.hel.haitaton.hanke.allu.ApplicationStatus.PENDING_CLIENT
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContent
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentSummary
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentSummaryRepository
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentService(
    private val cableReportService: CableReportService,
    private val applicationRepository: ApplicationRepository,
    private val summaryRepository: ApplicationAttachmentSummaryRepository,
    private val contentRepository: ApplicationAttachmentContentRepository,
    private val scanClient: FileScanClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Transactional(readOnly = true)
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        summaryRepository.findByApplicationId(applicationId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getContent(applicationId: Long, attachmentId: UUID): AttachmentContent {
        val attachmentContent = findContent(applicationId, attachmentId)
        with(attachmentContent) {
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
            ApplicationAttachmentContent(
                id = null,
                fileName = attachment.originalFilename!!,
                content = attachment.bytes,
                contentType = attachment.contentType!!,
                createdByUserId = currentUserId(),
                createdAt = now(),
                scanStatus = OK,
                attachmentType = attachmentType,
                applicationId = application.id!!,
            )

        val newAttachment = contentRepository.save(entity)

        application.alluid?.let { sendAttachment(it, newAttachment) } // no need to re-check status

        return newAttachment.toMetadata().also {
            logger.info { "Added attachment ${it.id} to application $applicationId" }
        }
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    @Transactional
    fun deleteAttachment(applicationId: Long, attachmentId: UUID) {
        val attachment = findSummary(applicationId, attachmentId)
        val application = findApplication(applicationId)

        if (isInAllu(application)) {
            logger.warn { "Application $applicationId is in Allu, attachments cannot be deleted." }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        summaryRepository.deleteAttachment(attachment.id!!)
        logger.info { "Deleted application attachment ${attachment.id}" }
    }

    @Transactional(readOnly = true)
    fun sendInitialAttachments(alluId: Int, applicationId: Long) {
        logger.info { "Sending initial attachments for application, alluid=$alluId" }
        val attachmentIds =
            summaryRepository.findByApplicationId(applicationId).mapNotNull { it.id }
        if (attachmentIds.isEmpty()) {
            logger.info { "No attachments to send for alluId $alluId" }
            return
        }

        runBlocking { sendAttachmentContents(alluId, attachmentIds) }
    }

    private fun findContent(applicationId: Long, attachmentId: UUID): ApplicationAttachmentContent =
        contentRepository.contentById(attachmentId).also {
            ensureIsFromApplication(
                attachmentId = attachmentId,
                actualApplicationId = it.applicationId,
                expectedApplicationId = applicationId
            )
        }

    private fun findSummary(applicationId: Long, attachmentId: UUID): ApplicationAttachmentSummary =
        summaryRepository.summaryById(attachmentId).also {
            ensureIsFromApplication(
                attachmentId = attachmentId,
                actualApplicationId = it.applicationId,
                expectedApplicationId = applicationId
            )
        }

    private fun ensureIsFromApplication(
        attachmentId: UUID,
        actualApplicationId: Long,
        expectedApplicationId: Long
    ) {
        if (actualApplicationId != expectedApplicationId) {
            logger.warn {
                "Requested attachment $attachmentId is not from application $expectedApplicationId."
            }
            throw AttachmentNotFoundException(attachmentId)
        }
    }

    private fun findApplication(applicationId: Long): ApplicationEntity =
        applicationRepository.findById(applicationId).orElseThrow {
            ApplicationNotFoundException(applicationId)
        }

    private suspend fun sendAttachmentContents(alluId: Int, attachmentIds: List<UUID>) {
        val token = cableReportService.login()
        withContext(ioDispatcher) {
            attachmentIds.forEach { id ->
                launch { findAndSendAttachmentContent(id, alluId, token) }
            }
        }
    }

    private fun findAndSendAttachmentContent(attachmentId: UUID, alluId: Int, token: String) {
        val content = contentRepository.contentById(attachmentId)
        sendAttachment(alluId, content, token)
    }

    /** Attachment should be sent if application is in Allu. Must check status before sending. */
    private fun sendAttachment(
        alluId: Int,
        attachment: ApplicationAttachmentContent,
        token: String? = null
    ) {
        cableReportService.addAttachment(alluId, attachment.toAlluAttachment(), token)
    }

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
}

class ApplicationInAlluException(id: Long?, alluId: Int?) :
    RuntimeException("Application is already sent to Allu, applicationId=$id, alluId=$alluId")
