package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
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
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
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

        if (isInAllu(application)) {
            logger.warn {
                "Application is in Allu, attachments cannot be added. applicationId = $applicationId, alluid = ${application.alluid}"
            }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        val contentType = ensureContentTypeIsValid(attachment.contentType)
        scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(applicationId)

        logger.info { "Saving attachment content for application $applicationId" }
        val blobPath =
            attachmentContentService.upload(filename, contentType, attachment.bytes, applicationId)
        logger.info { "Saving attachment metadata for application $applicationId" }
        val newAttachment =
            try {
                metadataService.create(
                    filename,
                    contentType.toString(),
                    attachment.size,
                    blobPath,
                    attachmentType,
                    applicationId
                )
            } catch (e: Exception) {
                logger.error(e) {
                    "Attachment metadata save failed, deleting attachment content $blobPath"
                }
                attachmentContentService.delete(blobPath)
                throw e
            }

        logger.info {
            "Added attachment metadata ${newAttachment.id} and content $blobPath for application $applicationId"
        }
        return newAttachment.toDto()
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    fun deleteAttachment(attachmentId: UUID) {
        val attachment = metadataService.findAttachment(attachmentId)
        val application = findApplication(attachment.applicationId)

        if (isInAllu(application)) {
            logger.warn {
                "Application is in Allu, attachments cannot be deleted. applicationId = ${application.id}, alluid = ${application.alluid}"
            }
            throw ApplicationInAlluException(application.id, application.alluid)
        }

        logger.info { "Deleting attachment metadata ${attachment.id}" }
        metadataService.deleteAttachmentById(attachment.id)
        logger.info { "Deleting attachment content at ${attachment.blobLocation}" }
        attachmentContentService.delete(attachment.blobLocation)
        logger.info { "Deleted attachment $attachmentId from application ${application.id}" }
    }

    fun deleteAllAttachments(hakemus: HakemusIdentifier) {
        logger.info { "Deleting all attachments from application. ${hakemus.logString()}" }
        metadataService.deleteAllAttachments(hakemus)
        try {
            attachmentContentService.deleteAllForApplication(hakemus)
            logger.info { "Deleted all attachments from application. ${hakemus.logString()}" }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to delete all attachment content for application. Continuing with application deletion regardless of error. ${hakemus.logString()}"
            }
        }
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

    private fun ensureContentTypeIsValid(contentType: String?): MediaType =
        contentType?.let { MediaType.parseMediaType(it) }
            ?: throw AttachmentInvalidException("Content-type was not set")

    private fun isInAllu(application: Application): Boolean = application.alluid != null
}
