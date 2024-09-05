package fi.hel.haitaton.hanke.valmistumisilmoitus

import java.time.LocalDate
import java.time.OffsetDateTime

data class ValmistumisilmoitusResponse(
    val type: ValmistumisilmoitusType,
    val dateReported: LocalDate,
    val reportedAt: OffsetDateTime,
)
