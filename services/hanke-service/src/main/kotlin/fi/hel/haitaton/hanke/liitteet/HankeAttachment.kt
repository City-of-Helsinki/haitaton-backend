package fi.hel.haitaton.hanke.liitteet

import java.time.LocalDateTime
import java.util.*

data class HankeAttachment(
    val id: UUID?,
    val name: String?,
    val user: String?,
    var created: LocalDateTime?,
    var tila: AttachmentTila?,
    var hankeId: Int?,
)
