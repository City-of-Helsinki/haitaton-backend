package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import java.util.UUID

data class Taydennys(
    override val id: UUID,
    val taydennyspyyntoId: UUID,
    val hakemusData: HakemusData,
) : HasId<UUID> {
    fun toResponse() = TaydennysResponse(id, hakemusData.toResponse())
}

data class TaydennysResponse(override val id: UUID, val applicationData: HakemusDataResponse) :
    HasId<UUID>
