package fi.hel.haitaton.hanke.paatos

import java.util.UUID

data class PaatosMetadata(
    val id: UUID,
    val hakemusId: Long,
    val hakemustunnus: String,
    val tyyppi: PaatosTyyppi,
    val tila: PaatosTila,
)
