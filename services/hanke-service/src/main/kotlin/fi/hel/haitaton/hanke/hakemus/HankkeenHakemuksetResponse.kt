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
        hakemus: Hakemus
    ) : this(
        hakemus.id,
        hakemus.alluid,
        hakemus.alluStatus,
        hakemus.applicationIdentifier,
        hakemus.applicationType,
        when (hakemus.applicationData) {
            is JohtoselvityshakemusData -> HankkeenHakemusDataResponse(hakemus.applicationData)
            is KaivuilmoitusData -> HankkeenHakemusDataResponse(hakemus.applicationData)
        },
    )
}

data class HankkeenHakemusDataResponse(
    val name: String,
    val startTime: ZonedDateTime?,
    val endTime: ZonedDateTime?,
    val pendingOnClient: Boolean,
) {
    constructor(
        cableReportApplicationData: JohtoselvityshakemusData
    ) : this(
        cableReportApplicationData.name,
        cableReportApplicationData.startTime,
        cableReportApplicationData.endTime,
        cableReportApplicationData.pendingOnClient,
    )

    constructor(
        kaivuilmoitusEntityData: KaivuilmoitusData
    ) : this(
        kaivuilmoitusEntityData.name,
        kaivuilmoitusEntityData.startTime,
        kaivuilmoitusEntityData.endTime,
        kaivuilmoitusEntityData.pendingOnClient,
    )
}
