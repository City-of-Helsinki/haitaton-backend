package fi.hel.haitaton.hanke.ilmoitus

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

object IlmoitusFactory {
    val DEFAULT_ID: UUID = UUID.fromString("979b4ed8-0e72-44fd-b31b-b36bc3542308")
    val DEFAULT_DATE = LocalDate.of(2024, 8, 8)
    val DEFAULT_REPORT_TIME = OffsetDateTime.parse("2024-08-08T15:12:41Z")

    fun create(
        id: UUID = DEFAULT_ID,
        type: IlmoitusType = IlmoitusType.TOIMINNALLINEN_KUNTO,
        dateReported: LocalDate = DEFAULT_DATE,
        reportedAt: OffsetDateTime = DEFAULT_REPORT_TIME,
    ) = Ilmoitus(id, type, dateReported, reportedAt)
}
