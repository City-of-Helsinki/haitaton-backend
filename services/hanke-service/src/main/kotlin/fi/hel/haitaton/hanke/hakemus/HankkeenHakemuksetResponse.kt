package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import java.time.ZonedDateTime

data class HankkeenHakemuksetResponse(val applications: List<HankkeenHakemusResponse>)

data class HankkeenHakemusResponse(
    val id: Long,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: HankkeenHakemusDataResponse,
) {
    constructor(
        hakemus: Hakemus,
        includeAreas: Boolean = false,
    ) : this(
        hakemus.id,
        hakemus.alluid,
        hakemus.alluStatus,
        hakemus.applicationIdentifier,
        hakemus.applicationType,
        when (hakemus.applicationData) {
            is JohtoselvityshakemusData ->
                HankkeenHakemusDataResponse(hakemus.applicationData, includeAreas)
            is KaivuilmoitusData ->
                HankkeenHakemusDataResponse(hakemus.applicationData, includeAreas)
        },
    )
}

data class HankkeenHakemusDataResponse(
    val name: String,
    val startTime: ZonedDateTime?,
    val endTime: ZonedDateTime?,
    val areas: List<Hakemusalue>?,
) {
    constructor(
        cableReportApplicationData: JohtoselvityshakemusData,
        includeAreas: Boolean,
    ) : this(
        cableReportApplicationData.name,
        cableReportApplicationData.startTime,
        cableReportApplicationData.endTime,
        if (includeAreas) cableReportApplicationData.areas else null,
    )

    constructor(
        kaivuilmoitusEntityData: KaivuilmoitusData,
        includeAreas: Boolean,
    ) : this(
        kaivuilmoitusEntityData.name,
        kaivuilmoitusEntityData.startTime,
        kaivuilmoitusEntityData.endTime,
        if (includeAreas) kaivuilmoitusEntityData.areas else null,
    )
}
