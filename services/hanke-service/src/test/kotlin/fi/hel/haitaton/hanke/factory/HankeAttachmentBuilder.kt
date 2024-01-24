package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentContentService.Companion.generateBlobPath
import org.springframework.http.MediaType

data class HankeAttachmentBuilder(
    val value: HankeAttachmentEntity,
    val attachmentRepository: HankeAttachmentRepository,
    val hankeAttachmentFactory: HankeAttachmentFactory
) {
    fun withCloudContent(
        path: String = generateBlobPath(value.hanke.id),
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MediaType.APPLICATION_PDF,
        bytes: ByteArray = DEFAULT_DATA
    ): HankeAttachmentBuilder {
        this.value.blobLocation = path
        this.value.size = bytes.size.toLong()
        attachmentRepository.save(value)
        hankeAttachmentFactory.saveContentToCloud(path, filename, mediaType, bytes)
        return this
    }

    fun toDomain() = value.toDomain()
}
