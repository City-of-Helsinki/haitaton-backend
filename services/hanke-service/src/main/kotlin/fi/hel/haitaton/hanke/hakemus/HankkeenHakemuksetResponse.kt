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
        application: HakemusEntity
    ) : this(
        application.id,
        application.alluid,
        application.alluStatus,
        application.applicationIdentifier,
        application.applicationType,
        when (application.hakemusEntityData) {
            is JohtoselvityshakemusEntityData ->
                HankkeenHakemusDataResponse(
                    application.hakemusEntityData as JohtoselvityshakemusEntityData
                )
            is KaivuilmoitusEntityData ->
                HankkeenHakemusDataResponse(
                    application.hakemusEntityData as KaivuilmoitusEntityData
                )
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
        cableReportApplicationData: JohtoselvityshakemusEntityData
    ) : this(
        cableReportApplicationData.name,
        cableReportApplicationData.startTime,
        cableReportApplicationData.endTime,
        cableReportApplicationData.pendingOnClient,
    )

    constructor(
        kaivuilmoitusEntityData: KaivuilmoitusEntityData
    ) : this(
        kaivuilmoitusEntityData.name,
        kaivuilmoitusEntityData.startTime,
        kaivuilmoitusEntityData.endTime,
        kaivuilmoitusEntityData.pendingOnClient
    )
}
