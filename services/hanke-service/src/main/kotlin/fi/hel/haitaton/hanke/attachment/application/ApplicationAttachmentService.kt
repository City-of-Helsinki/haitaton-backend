package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
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
    private val contentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) : AttachmentService<HakemusIdentifier, ApplicationAttachmentMetadata> {

    /** Authorization in controller throws exception if application ID is unknown. */
    fun getMetadataList(applicationId: Long): List<ApplicationAttachmentMetadata> =
        metadataService.getMetadataList(applicationId)

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
        scanClient.scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(applicationId)

        val newAttachment =
            saveAttachment(hakemus, attachment.bytes, filename, contentType, attachmentType)

        return newAttachment.toDto()
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
        contentService.delete(attachment.blobLocation)
        logger.info { "Deleted attachment $attachmentId from application ${hakemus.id}" }
    }

    fun deleteAllAttachments(hakemus: HakemusIdentifier) {
        logger.info { "Deleting all attachments from application. ${hakemus.logString()}" }
        metadataService.deleteAllAttachments(hakemus)
        try {
            contentService.deleteAllForApplication(hakemus)
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

        alluClient.addAttachments(alluId, attachments) { contentService.find(it) }
    }

    private fun findHakemus(applicationId: Long): HakemusIdentifier =
        hakemusRepository.findByIdOrNull(applicationId)?.toMetadata()
            ?: throw HakemusNotFoundException(applicationId)

    private fun isInAllu(hakemus: HakemusIdentifier): Boolean = hakemus.alluid != null

    override fun findMetadata(attachmentId: UUID): ApplicationAttachmentMetadata =
        metadataService.findAttachment(attachmentId)

    override fun findContent(attachment: ApplicationAttachmentMetadata): ByteArray =
        contentService.find(attachment)

    override fun upload(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        entity: HakemusIdentifier,
    ): String = contentService.upload(filename, contentType, content, entity.id)

    override fun createMetadata(
        filename: String,
        contentType: String,
        size: Long,
        blobPath: String,
        entity: HakemusIdentifier,
        attachmentType: ApplicationAttachmentType?,
    ): ApplicationAttachmentMetadata =
        metadataService.create(filename, contentType, size, blobPath, attachmentType!!, entity.id)

    override fun delete(blobPath: String) = contentService.delete(blobPath)
}
