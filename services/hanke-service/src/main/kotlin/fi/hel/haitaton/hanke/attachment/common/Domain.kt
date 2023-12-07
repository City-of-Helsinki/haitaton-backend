package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.allu.Attachment as AlluAttachment
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import java.time.OffsetDateTime
import java.util.UUID

sealed interface Attachment {
    val id: UUID
    val fileName: String
    val contentType: String
    val createdByUserId: String
    val createdAt: OffsetDateTime
    val blobLocation: String?
}

data class ApplicationAttachment(
    override val id: UUID,
    override val fileName: String,
    override val contentType: String,
    override val createdByUserId: String,
    override val createdAt: OffsetDateTime,
    override val blobLocation: String?,
    val applicationId: Long,
    val attachmentType: ApplicationAttachmentType,
) : Attachment {
    fun toDto(): ApplicationAttachmentMetadata {
        return ApplicationAttachmentMetadata(
            id = id,
            fileName = fileName,
            createdAt = createdAt,
            createdByUserId = createdByUserId,
            applicationId = applicationId,
            attachmentType = attachmentType,
        )
    }

    fun toAlluAttachment(content: ByteArray): AlluAttachment {
        return AlluAttachment(
            metadata =
                AttachmentMetadata(
                    id = null,
                    mimeType = contentType,
                    name = fileName,
                    description = null,
                ),
            file = content
        )
    }
}
