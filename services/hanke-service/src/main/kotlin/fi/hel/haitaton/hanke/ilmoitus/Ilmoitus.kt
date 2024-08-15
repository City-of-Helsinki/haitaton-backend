package fi.hel.haitaton.hanke.ilmoitus

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class Ilmoitus(
    val id: UUID,
    val type: IlmoitusType,
    val dateReported: LocalDate,
    val createdAt: OffsetDateTime,
) {
    fun toResponse() = IlmoitusResponse(type, dateReported, createdAt)
}
