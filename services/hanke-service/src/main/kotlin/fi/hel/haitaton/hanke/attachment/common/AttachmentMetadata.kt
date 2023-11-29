package fi.hel.haitaton.hanke.attachment.common

import java.time.OffsetDateTime
import java.util.UUID

data class HankeAttachment(
    val id: UUID,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val hankeTunnus: String,
)

data class ApplicationAttachmentMetadata(
    val id: UUID,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val applicationId: Long,
    val attachmentType: ApplicationAttachmentType,
)

data class AttachmentContent(
    val fileName: String,
    val contentType: String,
    @Suppress("ArrayInDataClass") val bytes: ByteArray
)

data class UnmigratedHankeAttachment(
    val id: UUID,
    val hankeId: Int,
    val content: AttachmentContent
)
