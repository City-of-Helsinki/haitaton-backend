package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusResponse
import fi.hel.haitaton.hanke.paatos.Paatos
import fi.hel.haitaton.hanke.paatos.PaatosResponse
import fi.hel.haitaton.hanke.taydennys.TaydennysWithExtras
import fi.hel.haitaton.hanke.taydennys.TaydennysWithExtrasResponse
import fi.hel.haitaton.hanke.taydennys.Taydennyspyynto
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoResponse

data class HakemusWithExtras(
    val hakemus: Hakemus,
    val paatokset: List<Paatos>,
    val taydennyspyynto: Taydennyspyynto?,
    val taydennys: TaydennysWithExtras?,
    val muutosilmoitus: Muutosilmoitus?,
) {
    fun toResponse(): HakemusWithExtrasResponse =
        HakemusWithExtrasResponse(
            hakemus.toResponse(),
            paatokset.map { it.toResponse() }.groupBy { it.hakemustunnus },
            taydennyspyynto?.toResponse(),
            taydennys?.toResponse(),
            muutosilmoitus?.toResponse(),
        )
}

@JsonInclude(Include.NON_NULL)
data class HakemusWithExtrasResponse(
    @JsonUnwrapped @JsonInclude(Include.ALWAYS) val hakemus: HakemusResponse,
    val paatokset: Map<String, List<PaatosResponse>>,
    val taydennyspyynto: TaydennyspyyntoResponse?,
    val taydennys: TaydennysWithExtrasResponse?,
    val muutosilmoitus: MuutosilmoitusResponse?,
)
