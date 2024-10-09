package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.paatos.Paatos
import fi.hel.haitaton.hanke.paatos.PaatosResponse
import fi.hel.haitaton.hanke.taydennys.Taydennyspyynto
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoResponse

data class HakemusWithExtras(
    val hakemus: Hakemus,
    val paatokset: List<Paatos>,
    val taydennyspyynto: Taydennyspyynto?,
) {
    fun toResponse(): HakemusWithExtrasResponse =
        HakemusWithExtrasResponse(
            hakemus.toResponse(),
            paatokset.map { it.toResponse() }.groupBy { it.hakemustunnus },
            taydennyspyynto?.toResponse(),
        )
}

data class HakemusWithExtrasResponse(
    @JsonUnwrapped val hakemus: HakemusResponse,
    val paatokset: Map<String, List<PaatosResponse>>,
    val taydennyspyynto: TaydennyspyyntoResponse?,
)
