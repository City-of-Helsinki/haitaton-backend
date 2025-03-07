package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import fi.hel.haitaton.hanke.hakemus.HakemusIdentifier
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class ApplicationAttachmentService(
    private val alluClient: AlluClient,
    private val metadataService: ApplicationAttachmentMetadataService,
    private val hakemusRepository: HakemusRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) {
    /** Authorization in controller throws exception if application ID is unknown. */
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        metadataService.getMetadataList(applicationId)

    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = metadataService.findAttachment(attachmentId)

        if (attachment.attachmentType == ApplicationAttachmentType.VALTAKIRJA) {
            throw ValtakirjaForbiddenException(attachmentId)
        }

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
        attachment: MultipartFile,
    ): ApplicationAttachmentMetadataDto {
        logger.info {
            "Adding attachment to application, applicationId = $applicationId, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }
        AttachmentValidator.validateSize(attachment.bytes.size)
        val filename = AttachmentValidator.validFilename(attachment.originalFilename)
        AttachmentValidator.validateExtensionForType(filename, attachmentType)
        val hakemus = findHakemus(applicationId)

        if (isInAllu(hakemus)) {
            logger.warn {
                "Application is in Allu, attachments cannot be added. ${hakemus.logString()}"
            }
            throw ApplicationInAlluException(hakemus.id, hakemus.alluid)
        }

        val contentType = AttachmentValidator.ensureMediaType(attachment.contentType)
        scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(applicationId)

        val newAttachment =
            saveAttachment(hakemus, attachment.bytes, filename, contentType, attachmentType)

        return newAttachment.toDto()
    }

    fun saveAttachment(
        hakemus: HakemusIdentifier,
        content: ByteArray,
        filename: String,
        contentType: MediaType,
        attachmentType: ApplicationAttachmentType,
    ): ApplicationAttachmentMetadata {
        logger.info { "Saving attachment content for application. ${hakemus.logString()}" }
        val blobPath = attachmentContentService.upload(filename, contentType, content, hakemus.id)
        logger.info { "Saving attachment metadata for application. ${hakemus.logString()}" }
        val newAttachment =
            try {
                metadataService.create(
                    filename,
                    contentType.toString(),
                    content.size.toLong(),
                    blobPath,
                    attachmentType,
                    hakemus.id,
                )
            } catch (e: Exception) {
                logger.error(e) {
                    "Attachment metadata save failed, deleting attachment content $blobPath"
                }
                attachmentContentService.delete(blobPath)
                throw e
            }
        logger.info {
            "Added attachment metadata ${newAttachment.id} and content $blobPath for application. ${hakemus.logString()}"
        }
        return newAttachment
    }

    /** Attachment can be deleted if the application has not been sent to Allu (alluId null). */
    fun deleteAttachment(attachmentId: UUID) {
        val attachment = metadataService.findAttachment(attachmentId)
        val hakemus = findHakemus(attachment.applicationId)

        if (isInAllu(hakemus)) {
            logger.warn {
                "Application is in Allu, attachments cannot be deleted. ${hakemus.logString()}"
            }
            throw ApplicationInAlluException(hakemus.id, hakemus.alluid)
        }

        logger.info { "Deleting attachment metadata ${attachment.id}" }
        metadataService.deleteAttachmentById(attachment.id)
        logger.info { "Deleting attachment content at ${attachment.blobLocation}" }
        attachmentContentService.delete(attachment.blobLocation)
        logger.info { "Deleted attachment $attachmentId from application ${hakemus.id}" }
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

        alluClient.addAttachments(alluId, attachments) { attachmentContentService.find(it) }
    }

    private fun findHakemus(applicationId: Long): HakemusIdentifier =
        hakemusRepository.findByIdOrNull(applicationId)?.toMetadata()
            ?: throw HakemusNotFoundException(applicationId)

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }

    private fun isInAllu(hakemus: HakemusIdentifier): Boolean = hakemus.alluid != null
}
