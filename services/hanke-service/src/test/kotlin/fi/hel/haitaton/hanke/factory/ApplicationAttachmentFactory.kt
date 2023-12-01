package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

@Component
class ApplicationAttachmentFactory(
    private val applicationAttachmentRepository: ApplicationAttachmentRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
) {
    fun saveAttachment(applicationId: Long): ApplicationAttachmentEntity {
        val attachment =
            applicationAttachmentRepository.save(createEntity(applicationId = applicationId))
        attachmentContentService.save(attachment.id!!, DUMMY_DATA)
        return attachment
    }

    companion object {
        val defaultAttachmentId: UUID = UUID.fromString("5cba3a76-28ad-42aa-b7e6-b5c1775be81a")
        val CREATED_AT: OffsetDateTime = OffsetDateTime.parse("2023-11-09T10:03:55+02:00")

        const val FILE_NAME = "file.pdf"

        fun createEntity(
            id: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            blobLocation: String? = null,
            createdByUserId: String = USERNAME,
            createdAt: OffsetDateTime = CREATED_AT,
            attachmentType: ApplicationAttachmentType = MUU,
            applicationId: Long,
        ): ApplicationAttachmentEntity =
            ApplicationAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                blobLocation = blobLocation,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                applicationId = applicationId,
            )

        fun createMetadata(
            attachmentId: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            createdBy: String = currentUserId(),
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            applicationId: Long = 1L,
            attachmentType: ApplicationAttachmentType = MUU,
        ): ApplicationAttachmentMetadata =
            ApplicationAttachmentMetadata(
                id = attachmentId,
                fileName = fileName,
                createdByUserId = createdBy,
                createdAt = createdAt,
                applicationId = applicationId,
                attachmentType = attachmentType
            )

        fun createContent(
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            bytes: ByteArray = DUMMY_DATA,
        ) = AttachmentContent(fileName = fileName, contentType = contentType, bytes = bytes)
    }
}
