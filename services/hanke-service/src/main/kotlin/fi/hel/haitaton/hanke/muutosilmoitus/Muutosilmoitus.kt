package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.hakemus.HakemusData
import java.time.OffsetDateTime
import java.util.UUID

data class Muutosilmoitus(
    override val id: UUID,
    override val hakemusId: Long,
    val sent: OffsetDateTime?,
    val hakemusData: HakemusData,
) : MuutosilmoitusIdentifier {
    fun toResponse() = MuutosilmoitusResponse(id, sent, hakemusData.toResponse())
}
