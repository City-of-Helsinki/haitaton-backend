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
    fun saveHankeContent(attachmentId: UUID, content: ByteArray) {
        hankeAttachmentContentRepository.save(HankeAttachmentContentEntity(attachmentId, content))
    }

    fun findHankeContent(attachment: HankeAttachmentEntity): ByteArray =
        attachment.blobLocation?.let { readHankeAttachmentFromFile(it, attachment.id!!) }
            ?: readHankeAttachmentFromDatabase(attachment.id!!)

    fun readHankeAttachmentFromFile(location: String, attachmentId: UUID): ByteArray =
        try {
            fileClient.download(Container.HANKE_LIITTEET, location).content.toBytes()
        } catch (e: DownloadNotFoundException) {
            throw AttachmentNotFoundException(attachmentId)
        }

    fun readHankeAttachmentFromDatabase(attachmentId: UUID): ByteArray =
        hankeAttachmentContentRepository.findByIdOrNull(attachmentId)?.content
            ?: run {
                logger.error { "Content not found for hanke attachment $attachmentId" }
                throw AttachmentNotFoundException(attachmentId)
            }
}
