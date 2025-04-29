package fi.hel.haitaton.hanke.muutosilmoitus

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentMetadataDto
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import java.time.OffsetDateTime
import java.util.UUID

data class MuutosilmoitusResponse(
    override val id: UUID,
    val sent: OffsetDateTime?,
    val applicationData: HakemusDataResponse,
) : HasId<UUID> {
    fun withExtras(
        muutokset: List<String>,
        liitteet: List<MuutosilmoitusAttachmentMetadata>,
    ): MuutosilmoitusWithExtrasResponse =
        MuutosilmoitusWithExtrasResponse(this, muutokset, liitteet.map { it.toDto() })
}

data class MuutosilmoitusWithExtrasResponse(
    @JsonUnwrapped val muutosilmoitus: MuutosilmoitusResponse,
    @JsonInclude(JsonInclude.Include.NON_NULL) val muutokset: List<String>?,
    val liitteet: List<MuutosilmoitusAttachmentMetadataDto>,
)
