package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.liitteet.AttachmentTila
import fi.hel.haitaton.hanke.liitteet.HankeAttachment
import java.time.LocalDateTime
import java.util.*

object AttachmentFactory {

    fun create(
        hankeId: Int,
        id: UUID? = UUID.randomUUID(),
        name: String? = "file.pdf",
        user: String? = currentUserId(),
        created: LocalDateTime? = DateFactory.getStartDatetime().toLocalDateTime(),
        tila: AttachmentTila? = AttachmentTila.OK,
    ): HankeAttachment {
        return HankeAttachment(id, name, user, created, tila, hankeId)
    }
}
