package fi.hel.haitaton.hanke.liitteet

import java.time.LocalDateTime
import java.util.UUID

data class AttachmentMetadata(
    val id: UUID?,
    val name: String?,
    val createdByUserId: String?,
    var createdAt: LocalDateTime?,
    var scanStatus: AttachmentScanStatus?,
    var hankeTunnus: String,
)
