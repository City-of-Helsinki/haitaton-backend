package fi.hel.haitaton.hanke.attachment

import java.time.LocalDateTime
import java.util.UUID

data class AttachmentMetadata(
    val id: UUID?,
    val fileName: String?,
    val createdByUserId: String?,
    val createdAt: LocalDateTime?,
    val scanStatus: AttachmentScanStatus?,
    val hankeTunnus: String,
)
