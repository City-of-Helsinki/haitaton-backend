package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentMetadataWithType
import java.time.OffsetDateTime
import java.util.UUID

data class MuutosilmoitusAttachmentMetadata(
    override val id: UUID,
    override val fileName: String,
    override val contentType: String,
    override val size: Long,
    override val createdByUserId: String,
    override val createdAt: OffsetDateTime,
    override val blobLocation: String,
    val muutosilmoitusId: UUID,
    override val attachmentType: ApplicationAttachmentType,
) : AttachmentMetadataWithType {
    fun toDto(): MuutosilmoitusAttachmentMetadataDto {
        return MuutosilmoitusAttachmentMetadataDto(
            id = id,
            fileName = fileName,
            contentType = contentType,
            size = size,
            attachmentType = attachmentType,
            createdAt = createdAt,
            createdByUserId = createdByUserId,
            muutosilmoitusId = muutosilmoitusId,
        )
    }
}
