package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.paatos.Paatos
import fi.hel.haitaton.hanke.paatos.PaatosResponse
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID

data class HankkeenHakemuksetResponse(val applications: List<HankkeenHakemusResponse>)

data class HankkeenHakemusResponse(
    val id: Long,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: HankkeenHakemusDataResponse,
    @JsonInclude(Include.NON_NULL) val muutosilmoitus: HankkeenHakemusMuutosilmoitusResponse?,
    val paatokset: Map<String, List<PaatosResponse>>,
) {
    constructor(
        hakemus: HakemusEntity,
        muutosilmoitus: MuutosilmoitusEntity?,
        paatokset: List<Paatos>,
        includeAreas: Boolean,
    ) : this(
        hakemus.id,
        hakemus.alluid,
        hakemus.alluStatus,
        hakemus.applicationIdentifier,
        hakemus.applicationType,
        when (val data = hakemus.hakemusEntityData) {
            is JohtoselvityshakemusEntityData -> HankkeenHakemusDataResponse(data, includeAreas)
            is KaivuilmoitusEntityData -> HankkeenHakemusDataResponse(data, includeAreas)
        },
        muutosilmoitus?.let { HankkeenHakemusMuutosilmoitusResponse(muutosilmoitus, includeAreas) },
        paatokset.map { it.toResponse() }.groupBy { it.hakemustunnus },
    )
}

data class HankkeenHakemusDataResponse(
    val name: String,
    val startTime: ZonedDateTime?,
    val endTime: ZonedDateTime?,
    val areas: List<Hakemusalue>?,
) {
    constructor(
        cableReportApplicationData: JohtoselvityshakemusEntityData,
        includeAreas: Boolean,
    ) : this(
        cableReportApplicationData.name,
        cableReportApplicationData.startTime,
        cableReportApplicationData.endTime,
        if (includeAreas) cableReportApplicationData.areas else null,
    )

    constructor(
        kaivuilmoitusEntityData: KaivuilmoitusEntityData,
        includeAreas: Boolean,
    ) : this(
        kaivuilmoitusEntityData.name,
        kaivuilmoitusEntityData.startTime,
        kaivuilmoitusEntityData.endTime,
        if (includeAreas) kaivuilmoitusEntityData.areas else null,
    )
}

data class HankkeenHakemusMuutosilmoitusResponse(
    val id: UUID,
    val sent: OffsetDateTime?,
    val hakemusdata: HankkeenHakemusDataResponse,
) {
    constructor(
        muutosilmoitus: MuutosilmoitusEntity,
        includeAreas: Boolean,
    ) : this(
        id = muutosilmoitus.id,
        sent = muutosilmoitus.sent,
        hakemusdata =
            when (val data = muutosilmoitus.hakemusData) {
                is JohtoselvityshakemusEntityData -> HankkeenHakemusDataResponse(data, includeAreas)
                is KaivuilmoitusEntityData -> HankkeenHakemusDataResponse(data, includeAreas)
            },
    )
}
