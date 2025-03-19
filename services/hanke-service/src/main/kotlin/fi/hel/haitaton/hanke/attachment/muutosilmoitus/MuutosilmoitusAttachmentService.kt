package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentService
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusAlreadySentException
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusIdentifier
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusNotFoundException
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@Service
class MuutosilmoitusAttachmentService(
    private val metadataService: MuutosilmoitusAttachmentMetadataService,
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val contentService: ApplicationAttachmentContentService,
    private val scanClient: FileScanClient,
) : AttachmentService<MuutosilmoitusIdentifier, MuutosilmoitusAttachmentMetadata> {

    fun addAttachment(
        muutosilmoitusId: UUID,
        attachmentType: ApplicationAttachmentType,
        attachment: MultipartFile,
    ): MuutosilmoitusAttachmentMetadata {
        logger.info {
            "Adding attachment to muutosilmoitus, id = $muutosilmoitusId, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }
        AttachmentValidator.validateSize(attachment.bytes.size)
        val filename = AttachmentValidator.validFilename(attachment.originalFilename)
        AttachmentValidator.validateExtensionForType(filename, attachmentType)
        val muutosilmoitus = findMuutosilmoitus(muutosilmoitusId)

        if (muutosilmoitus.sent != null) {
            throw MuutosilmoitusAlreadySentException(muutosilmoitus)
        }

        val contentType = AttachmentValidator.ensureMediaType(attachment.contentType)
        scanClient.scanAttachment(filename, attachment.bytes)
        metadataService.ensureRoomForAttachment(muutosilmoitus)

        val newAttachment =
            saveAttachment(muutosilmoitus, attachment.bytes, filename, contentType, attachmentType)

        return newAttachment
    }

    private fun findMuutosilmoitus(muutosilmoitusId: UUID): MuutosilmoitusEntity =
        muutosilmoitusRepository.findByIdOrNull(muutosilmoitusId)
            ?: throw MuutosilmoitusNotFoundException(muutosilmoitusId)

    override fun upload(
        filename: String,
        contentType: MediaType,
        content: ByteArray,
        entity: MuutosilmoitusIdentifier,
    ): String = contentService.upload(filename, contentType, content, entity.hakemusId)

    override fun createMetadata(
        filename: String,
        contentType: String,
        size: Long,
        blobPath: String,
        entity: MuutosilmoitusIdentifier,
        attachmentType: ApplicationAttachmentType?,
    ): MuutosilmoitusAttachmentMetadata =
        metadataService.create(filename, contentType, size, blobPath, attachmentType!!, entity.id)

    override fun delete(blobPath: String): Boolean = contentService.delete(blobPath)
}
