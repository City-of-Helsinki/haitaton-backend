package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.allu.Attachment as AlluAttachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata as AlluAttachmentMetadata
import java.time.OffsetDateTime
import java.util.UUID

sealed interface AttachmentMetadata {
    val id: UUID
    val fileName: String
    val contentType: String
    val size: Long
    val createdByUserId: String
    val createdAt: OffsetDateTime
    val blobLocation: String?
}

data class HankeAttachmentMetadata(
    override val id: UUID,
    override val fileName: String,
    override val contentType: String,
    override val size: Long,
    override val createdByUserId: String,
    override val createdAt: OffsetDateTime,
    override val blobLocation: String,
    val hankeId: Int,
) : AttachmentMetadata

data class ApplicationAttachmentMetadata(
    override val id: UUID,
    override val fileName: String,
    override val contentType: String,
    override val size: Long,
    override val createdByUserId: String,
    override val createdAt: OffsetDateTime,
    override val blobLocation: String,
    val applicationId: Long,
    val attachmentType: ApplicationAttachmentType,
) : AttachmentMetadata {
    fun toDto(): ApplicationAttachmentMetadataDto {
        return ApplicationAttachmentMetadataDto(
            id = id,
            fileName = fileName,
            contentType = contentType,
            size = size,
            attachmentType = attachmentType,
            createdAt = createdAt,
            createdByUserId = createdByUserId,
            applicationId = applicationId,
        )
    }

    fun toAlluAttachment(content: ByteArray): AlluAttachment {
        return AlluAttachment(
            metadata =
                AlluAttachmentMetadata(
                    id = null,
                    mimeType = contentType,
                    name = fileName,
                    description = attachmentType.toFinnish(),
                ),
            file = content,
        )
    }
}

data class AttachmentContent(
    val fileName: String,
    val contentType: String,
    @Suppress("ArrayInDataClass") val bytes: ByteArray
)
