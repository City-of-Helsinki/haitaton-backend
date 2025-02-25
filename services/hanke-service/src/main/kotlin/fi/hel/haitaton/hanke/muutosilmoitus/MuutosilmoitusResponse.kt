package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import java.time.OffsetDateTime
import java.util.UUID

data class MuutosilmoitusResponse(
    override val id: UUID,
    val sent: OffsetDateTime?,
    val applicationData: HakemusDataResponse,
) : HasId<UUID>
