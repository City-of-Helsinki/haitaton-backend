package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.DEFAULT_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService.Companion.generateBlobPath
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import org.springframework.http.MediaType

data class ApplicationAttachmentBuilder(
    val value: ApplicationAttachmentEntity,
    val attachmentRepository: ApplicationAttachmentRepository,
    val applicationAttachmentFactory: ApplicationAttachmentFactory
) {
    fun withDbContent(bytes: ByteArray = DEFAULT_DATA): ApplicationAttachmentBuilder {
        applicationAttachmentFactory.saveContentToDb(value.id!!, bytes)
        return this
    }

    fun withCloudContent(
        path: String = generateBlobPath(value.applicationId),
        filename: String = FILE_NAME_PDF,
        mediaType: MediaType = ApplicationAttachmentFactory.MEDIA_TYPE,
        bytes: ByteArray = DEFAULT_DATA
    ): ApplicationAttachmentBuilder {
        this.value.blobLocation = path
        attachmentRepository.save(value)
        applicationAttachmentFactory.saveContent(path, filename, mediaType, bytes)
        return this
    }
}
