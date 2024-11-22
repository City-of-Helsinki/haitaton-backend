package fi.hel.haitaton.hanke.taydennys

import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import java.util.UUID

data class Taydennys(
    override val id: UUID,
    val taydennyspyyntoId: UUID,
    val hakemusData: HakemusData,
) : HasId<UUID> {
    fun toResponse() = TaydennysResponse(id, hakemusData.toResponse())

    fun withMuutokset(otherData: HakemusData): TaydennysWithMuutokset {
        return TaydennysWithMuutokset(
            id = id,
            taydennyspyyntoId = taydennyspyyntoId,
            hakemusData = hakemusData,
            muutokset = hakemusData.listChanges(otherData),
        )
    }
}

data class TaydennysResponse(override val id: UUID, val applicationData: HakemusDataResponse) :
    HasId<UUID> {
    fun withMuutokset(muutokset: List<String>): TaydennysWithMuutoksetResponse =
        TaydennysWithMuutoksetResponse(this, muutokset)
}

data class TaydennysWithMuutokset(
    override val id: UUID,
    val taydennyspyyntoId: UUID,
    val hakemusData: HakemusData,
    val muutokset: List<String>,
) : HasId<UUID> {
    fun toResponse() = TaydennysResponse(id, hakemusData.toResponse()).withMuutokset(muutokset)
}

data class TaydennysWithMuutoksetResponse(
    @JsonUnwrapped val taydennys: TaydennysResponse,
    val muutokset: List<String>,
)

/** Without application data, just the identifiers and metadata. */
data class TaydennysMetadata(
    override val id: UUID,
    val taydennyspyyntoId: UUID,
    val taydennyspyyntoAlluId: Int,
    val hakemusId: Long,
    val hakemustyyppi: ApplicationType,
) : TaydennysIdentifier {
    override fun taydennyspyyntoId(): UUID = taydennyspyyntoId

    override fun taydennyspyyntoAlluId(): Int = taydennyspyyntoAlluId

    override fun hakemusId(): Long = hakemusId

    override fun hakemustyyppi(): ApplicationType = hakemustyyppi
}
