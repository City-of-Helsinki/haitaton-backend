package fi.hel.haitaton.hanke.attachment.common

import java.time.OffsetDateTime
import java.util.UUID

data class HankeAttachmentMetadataDto(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val hankeTunnus: String,
)

data class ApplicationAttachmentMetadataDto(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val attachmentType: ApplicationAttachmentType,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val applicationId: Long,
)

data class TaydennysAttachmentMetadataDto(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val attachmentType: ApplicationAttachmentType,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val taydennysId: UUID,
)
