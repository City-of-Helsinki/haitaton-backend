package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentContentService
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentEntity
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID
import java.util.UUID.randomUUID
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

private const val FILE_NAME = "file.pdf"

private val dummyData = "ABC".toByteArray()

@Component
class AttachmentFactory(
    private val applicationAttachmentRepository: ApplicationAttachmentRepository,
    private val attachmentContentService: AttachmentContentService,
) {
    fun saveAttachment(applicationId: Long): ApplicationAttachmentEntity {
        val attachment =
            applicationAttachmentRepository.save(
                applicationAttachmentEntity(applicationId = applicationId)
            )
        attachmentContentService.saveApplicationContent(attachment.id!!, dummyData)
        return attachment
    }

    companion object {

        fun applicationAttachmentEntity(
            id: UUID = randomUUID(),
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
            id: UUID = randomUUID(),
            fileName: String = FILE_NAME,
            contentType: String = APPLICATION_PDF_VALUE,
            createdByUser: String = currentUserId(),
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
            )

        fun hankeAttachmentMetadata(
            attachmentId: UUID = randomUUID(),
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
            attachmentId: UUID = randomUUID(),
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
