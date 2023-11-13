package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import java.util.UUID
import mu.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class HankeAttachmentContentService(
    private val hankeAttachmentContentRepository: HankeAttachmentContentRepository,
    private val fileClient: FileClient,
) {
    fun save(attachmentId: UUID, content: ByteArray) {
        hankeAttachmentContentRepository.save(HankeAttachmentContentEntity(attachmentId, content))
    }

    fun delete(attachment: HankeAttachmentEntity) {
        attachment.blobLocation?.let { fileClient.delete(Container.HANKE_LIITTEET, it) }
    }

    fun find(attachment: HankeAttachmentEntity): ByteArray =
        attachment.blobLocation?.let { readFromFile(it, attachment.id!!) }
            ?: readFromDatabase(attachment.id!!)

    fun readFromFile(location: String, attachmentId: UUID): ByteArray =
        try {
            fileClient.download(Container.HANKE_LIITTEET, location).content.toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachmentId)
        }

    fun readFromDatabase(attachmentId: UUID): ByteArray =
        hankeAttachmentContentRepository.findByIdOrNull(attachmentId)?.content
            ?: run {
                logger.error { "Content not found for hanke attachment $attachmentId" }
                throw AttachmentNotFoundException(attachmentId)
            }
}
