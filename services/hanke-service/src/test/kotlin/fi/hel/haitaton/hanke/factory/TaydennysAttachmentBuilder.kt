package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import org.springframework.http.MediaType

data class TaydennysAttachmentBuilder(
    val value: TaydennysAttachmentEntity,
    val attachmentRepository: TaydennysAttachmentRepository,
    val attachmentFactory: TaydennysAttachmentFactory,
) {
    fun withContent(
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MediaType.APPLICATION_PDF,
        bytes: ByteArray = PDF_BYTES,
    ): TaydennysAttachmentBuilder {
        value.size = bytes.size.toLong()
        attachmentRepository.save(value)
        attachmentFactory.saveContent(value.blobLocation, filename, mediaType, bytes)
        return this
    }
}
