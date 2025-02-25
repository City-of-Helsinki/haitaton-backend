package fi.hel.haitaton.hanke.taydennys

import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import java.util.UUID

data class Taydennys(
    override val id: UUID,
    val taydennyspyyntoId: UUID,
    val hakemusId: Long,
    val hakemusData: HakemusData,
) : HasId<UUID> {
    fun toResponse() = TaydennysResponse(id, hakemusData.toResponse())

    fun withExtras(
        otherData: HakemusData,
        liitteet: List<TaydennysAttachmentMetadata>,
    ): TaydennysWithExtras {
        return TaydennysWithExtras(
            id = id,
            taydennyspyyntoId = taydennyspyyntoId,
            hakemusData = hakemusData,
            muutokset = hakemusData.listChanges(otherData),
            liitteet = liitteet,
        )
    }
}

data class TaydennysResponse(override val id: UUID, val applicationData: HakemusDataResponse) :
    HasId<UUID> {
    fun withExtras(
        muutokset: List<String>,
        liitteet: List<TaydennysAttachmentMetadata>,
    ): TaydennysWithExtrasResponse =
        TaydennysWithExtrasResponse(this, muutokset, liitteet.map { it.toDto() })
}

data class TaydennysWithExtras(
    override val id: UUID,
    val taydennyspyyntoId: UUID,
    val hakemusData: HakemusData,
    val muutokset: List<String>,
    val liitteet: List<TaydennysAttachmentMetadata>,
) : HasId<UUID> {
    fun toResponse() =
        TaydennysResponse(id, hakemusData.toResponse()).withExtras(muutokset, liitteet)
}

data class TaydennysWithExtrasResponse(
    @JsonUnwrapped val taydennys: TaydennysResponse,
    val muutokset: List<String>,
    val liitteet: List<TaydennysAttachmentMetadataDto>,
)
