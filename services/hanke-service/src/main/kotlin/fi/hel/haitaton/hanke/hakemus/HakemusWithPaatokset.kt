package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.paatos.Paatos
import fi.hel.haitaton.hanke.paatos.PaatosResponse

data class HakemusWithPaatokset(val hakemus: Hakemus, val paatokset: List<Paatos>) {
    fun toResponse(): HakemusWithPaatoksetResponse =
        HakemusWithPaatoksetResponse(
            hakemus.toResponse(),
            paatokset.map { it.toResponse() }.groupBy { it.hakemustunnus },
        )
}

data class HakemusWithPaatoksetResponse(
    @JsonUnwrapped val hakemus: HakemusResponse,
    val paatokset: Map<String, List<PaatosResponse>>,
)
