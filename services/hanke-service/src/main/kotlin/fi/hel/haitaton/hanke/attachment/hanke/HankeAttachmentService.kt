package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentPersister
import fi.hel.haitaton.hanke.attachment.common.FileScanClient
import fi.hel.haitaton.hanke.attachment.common.FileScanInput
import fi.hel.haitaton.hanke.attachment.common.HankeAttachment
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.hasInfected
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentService(
    private val hankeRepository: HankeRepository,
    private val attachmentRepository: HankeAttachmentRepository,
    private val attachmentContentService: HankeAttachmentContentService,
    private val persister: AttachmentPersister,
    private val scanClient: FileScanClient,
) {

    @Transactional(readOnly = true)
    fun getMetadataList(hankeTunnus: String): List<HankeAttachment> =
        findHanke(hankeTunnus).liitteet.map { it.toDomain() }

    @Transactional(readOnly = true)
    fun getContent(attachmentId: UUID): AttachmentContent {
        val attachment = findAttachment(attachmentId)

        val content = attachmentContentService.find(attachment)
        return AttachmentContent(attachment.fileName, attachment.contentType, content)
    }

    fun addAttachment(
        hanke: HankeIdentifier,
        name: String,
        type: MediaType,
        content: ByteArray
    ): HankeAttachment {
        scanAttachment(name, content)

        val blobPath =
            attachmentContentService.upload(
                fileName = name,
                contentType = type,
                content = content,
                hankeId = hanke.id
            )

        val entity = // transaction only after http calls.
            persister.hankeAttachment(
                filename = name,
                mediaType = type.toString(),
                blobPath = blobPath,
                hankeTunnus = hanke.hankeTunnus,
            )

        return entity.toDomain().also {
            logger.info { "Added attachment ${it.id} to hanke ${hanke.hankeTunnus}" }
        }
    }

    /** Move the attachment content to cloud. In test-data use for now, can be used for HAI-1964. */
    @Transactional
    fun moveToCloud(attachmentId: UUID): String {
        logger.info { "Moving attachment content to cloud for hanke attachment $attachmentId" }
        val attachment = findAttachment(attachmentId)
        return attachmentContentService.moveToCloud(attachment)
    }

    @Transactional
    fun deleteAttachment(attachmentId: UUID) {
        logger.info { "Deleting hanke attachment $attachmentId..." }
        val attachmentToDelete = findAttachment(attachmentId)
        attachmentContentService.delete(attachmentToDelete)
        attachmentToDelete.hanke.liitteet.remove(attachmentToDelete)
    }

    @Transactional
    fun deleteAllAttachments(hanke: HankeIdentifier) {
        logger.info { "Deleting all attachments from hanke ${hanke.logString()}" }
        attachmentContentService.deleteAllForHanke(hanke.id)
        val hankeEntity = hankeRepository.findByIdOrNull(hanke.id)
        hankeEntity?.liitteet?.clear()
    }

    @Transactional(readOnly = true)
    fun hankeWithRoomForAttachment(hankeTunnus: String): HankeIdentifier =
        findHankeIdentifiers(hankeTunnus).also {
            persister.checkRoomForAttachment(it.id, it.hankeTunnus)
        }

    private fun findAttachment(attachmentId: UUID) =
        attachmentRepository.findByIdOrNull(attachmentId)
            ?: throw AttachmentNotFoundException(attachmentId)

    private fun findHanke(hankeTunnus: String) =
        hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

    private fun findHankeIdentifiers(hankeTunnus: String) =
        hankeRepository.findOneByHankeTunnus(hankeTunnus)
            ?: throw HankeNotFoundException(hankeTunnus)

    private fun scanAttachment(filename: String, content: ByteArray) {
        val scanResult = scanClient.scan(listOf(FileScanInput(filename, content)))
        if (scanResult.hasInfected()) {
            throw AttachmentInvalidException("Infected file detected, see previous logs.")
        }
    }
}
