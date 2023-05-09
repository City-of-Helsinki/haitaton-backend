package fi.hel.haitaton.hanke.attachment.common

import java.time.OffsetDateTime
import java.util.UUID

data class HankeAttachmentMetadata(
    val id: UUID?,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val scanStatus: AttachmentScanStatus,
    val hankeTunnus: String,
)

data class ApplicationAttachmentMetadata(
    val id: UUID?,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val scanStatus: AttachmentScanStatus,
    val applicationId: Long,
    val attachmentType: ApplicationAttachmentType,
)

data class AttachmentContent(
    val fileName: String,
    val contentType: String,
    @Suppress("ArrayInDataClass") val bytes: ByteArray
)
