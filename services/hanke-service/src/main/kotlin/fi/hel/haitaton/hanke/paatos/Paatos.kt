package fi.hel.haitaton.hanke.paatos

import java.time.LocalDate
import java.util.UUID

data class Paatos(
    val id: UUID,
    val hakemusId: Long,
    val hakemustunnus: String,
    val tyyppi: PaatosTyyppi,
    val tila: PaatosTila,
    val nimi: String,
    val alkupaiva: LocalDate,
    val loppupaiva: LocalDate,
    val blobLocation: String,
    val size: Int,
)
