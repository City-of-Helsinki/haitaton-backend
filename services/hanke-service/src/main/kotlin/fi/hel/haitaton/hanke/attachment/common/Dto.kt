package fi.hel.haitaton.hanke.attachment.common

import java.time.OffsetDateTime
import java.util.UUID

data class HankeAttachmentMetadataDto(
    val id: UUID,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val hankeTunnus: String,
)

data class ApplicationAttachmentMetadataDto(
    val id: UUID,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val applicationId: Long,
    val attachmentType: ApplicationAttachmentType,
)
