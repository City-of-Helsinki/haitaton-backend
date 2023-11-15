package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

private val dummyData = "ABC".toByteArray()
private val defaultAttachmentId = UUID.fromString("5cba3a76-28ad-42aa-b7e6-b5c1775be81a")

@Component
class AttachmentFactory(
    private val applicationAttachmentRepository: ApplicationAttachmentRepository,
    private val attachmentContentService: ApplicationAttachmentContentService,
) {
    fun saveAttachment(applicationId: Long): ApplicationAttachmentEntity {
        val attachment =
            applicationAttachmentRepository.save(
                applicationAttachmentEntity(applicationId = applicationId)
            )
        attachmentContentService.save(attachment.id!!, dummyData)
        return attachment
    }

    companion object {
        const val FILE_NAME = "file.pdf"

        fun applicationAttachmentEntity(
            id: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            createdByUserId: String = "currentUserId",
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            attachmentType: ApplicationAttachmentType = MUU,
            applicationId: Long,
        ): ApplicationAttachmentEntity =
            ApplicationAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                createdByUserId = createdByUserId,
                createdAt = createdAt,
                attachmentType = attachmentType,
                applicationId = applicationId,
            )

        fun hankeAttachmentEntity(
            id: UUID? = defaultAttachmentId,
            fileName: String = FILE_NAME,
            blobLocation: String? = null,
            contentType: String = APPLICATION_PDF_VALUE,
            createdByUser: String = "currentUserId",
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            hanke: HankeEntity,
        ): HankeAttachmentEntity =
            HankeAttachmentEntity(
                id = id,
                fileName = fileName,
                contentType = contentType,
                createdByUserId = createdByUser,
                createdAt = createdAt,
                hanke = hanke,
                blobLocation = blobLocation,
            )

        fun hankeAttachmentMetadata(
            attachmentId: UUID = defaultAttachmentId,
            fileName: String = FILE_NAME,
            createdByUser: String = currentUserId(),
            createdAt: OffsetDateTime = OffsetDateTime.now(),
            hankeTunnus: String = "HAI-1234",
        ): HankeAttachmentMetadata =
            HankeAttachmentMetadata(
                id = attachmentId,
                fileName = fileName,
                createdByUserId = createdByUser,
                createdAt = createdAt,
                hankeTunnus = hankeTunnus,
            )

        fun applicationAttachmentMetadata(
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
    }
}
