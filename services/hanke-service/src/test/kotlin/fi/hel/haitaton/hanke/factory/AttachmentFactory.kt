package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.liitteet.AttachmentMetadata
import fi.hel.haitaton.hanke.liitteet.AttachmentScanStatus
import java.time.LocalDateTime
import java.util.UUID

object AttachmentFactory {

    fun create(
        hankeTunnus: String,
        id: UUID? = UUID.randomUUID(),
        name: String? = "file.pdf",
        user: String? = currentUserId(),
        created: LocalDateTime? = DateFactory.getStartDatetime().toLocalDateTime(),
        tila: AttachmentScanStatus? = AttachmentScanStatus.OK,
    ): AttachmentMetadata {
        return AttachmentMetadata(id, name, user, created, tila, hankeTunnus)
    }
}
