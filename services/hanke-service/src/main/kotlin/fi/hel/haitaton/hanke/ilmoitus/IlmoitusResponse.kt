package fi.hel.haitaton.hanke.ilmoitus

import java.time.LocalDate
import java.time.OffsetDateTime

data class IlmoitusResponse(
    val type: IlmoitusType,
    val dateReported: LocalDate,
    val reportedAt: OffsetDateTime,
)
