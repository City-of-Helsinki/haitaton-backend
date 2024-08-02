package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import org.springframework.http.MediaType

data class ApplicationAttachmentBuilder(
    val value: ApplicationAttachmentEntity,
    val attachmentRepository: ApplicationAttachmentRepository,
    val applicationAttachmentFactory: ApplicationAttachmentFactory
) {
    fun withContent(
        path: String = generateBlobPath(value.applicationId),
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = MediaType.APPLICATION_PDF,
        bytes: ByteArray = PDF_BYTES
    ): ApplicationAttachmentBuilder {
        this.value.blobLocation = path
        this.value.size = bytes.size.toLong()
        attachmentRepository.save(value)
        applicationAttachmentFactory.saveContent(path, filename, mediaType, bytes)
        return this
    }
}
