package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.attachment.AttachmentMetadata
import fi.hel.haitaton.hanke.attachment.AttachmentScanStatus
import fi.hel.haitaton.hanke.currentUserId
import java.time.OffsetDateTime
import java.util.UUID

object AttachmentFactory {

    fun create(
        hankeTunnus: String,
        id: UUID? = UUID.randomUUID(),
        fileName: String = "file.pdf",
        user: String = currentUserId(),
        created: OffsetDateTime = OffsetDateTime.now(),
        scanStatus: AttachmentScanStatus = AttachmentScanStatus.OK,
    ): AttachmentMetadata {
        return AttachmentMetadata(
            id = id,
            fileName = fileName,
            createdByUserId = user,
            createdAt = created,
            scanStatus = scanStatus,
            hankeTunnus = hankeTunnus,
        )
    }
}
