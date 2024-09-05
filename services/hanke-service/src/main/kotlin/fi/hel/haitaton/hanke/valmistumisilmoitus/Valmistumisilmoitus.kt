package fi.hel.haitaton.hanke.valmistumisilmoitus

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Valmistumisilmoitus(
    val id: UUID,
    val type: ValmistumisilmoitusType,
    val hakemustunnus: String,
    val dateReported: LocalDate,
    val createdAt: OffsetDateTime,
) {
    fun toResponse() = ValmistumisilmoitusResponse(type, dateReported, createdAt)
}
