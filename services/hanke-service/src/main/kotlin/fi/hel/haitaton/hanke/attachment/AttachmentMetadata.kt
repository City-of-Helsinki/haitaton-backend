package fi.hel.haitaton.hanke.attachment

import java.time.OffsetDateTime
import java.util.UUID

data class AttachmentMetadata(
    val id: UUID?,
    val fileName: String,
    val createdByUserId: String,
    val createdAt: OffsetDateTime,
    val scanStatus: AttachmentScanStatus,
    val hankeTunnus: String,
)
