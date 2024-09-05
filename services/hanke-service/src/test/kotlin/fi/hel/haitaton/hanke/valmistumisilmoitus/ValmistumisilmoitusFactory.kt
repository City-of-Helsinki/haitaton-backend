package fi.hel.haitaton.hanke.valmistumisilmoitus

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object ValmistumisilmoitusFactory {
    val DEFAULT_ID: UUID = UUID.fromString("979b4ed8-0e72-44fd-b31b-b36bc3542308")
    val DEFAULT_DATE = LocalDate.of(2024, 8, 8)
    val DEFAULT_REPORT_TIME = OffsetDateTime.parse("2024-08-08T15:12:41Z")
    val DEFAULT_HAKEMUSTUNNUS = "KP2400041-4"

    fun create(
        id: UUID = DEFAULT_ID,
        type: ValmistumisilmoitusType = ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO,
        hakemustunnus: String = DEFAULT_HAKEMUSTUNNUS,
        dateReported: LocalDate = DEFAULT_DATE,
        reportedAt: OffsetDateTime = DEFAULT_REPORT_TIME,
    ) = Valmistumisilmoitus(id, type, hakemustunnus, dateReported, reportedAt)
}
