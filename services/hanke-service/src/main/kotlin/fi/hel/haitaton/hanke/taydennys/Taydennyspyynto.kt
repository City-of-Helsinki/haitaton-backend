package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.domain.HasId
import java.util.UUID

data class Taydennyspyynto(
    override val id: UUID,
    val kentat: Map<InformationRequestFieldKey, String>,
) : HasId<UUID> {
    fun toResponse(): TaydennyspyyntoResponse =
        TaydennyspyyntoResponse(
            id = id,
            kentat = kentat.map { TaydennyspyyntoKenttaResponse(it.key, it.value) },
        )
}

data class TaydennyspyyntoResponse(val id: UUID, val kentat: List<TaydennyspyyntoKenttaResponse>)

data class TaydennyspyyntoKenttaResponse(val key: InformationRequestFieldKey, val message: String)
