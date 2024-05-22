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
        when (application.applicationData) {
            is CableReportApplicationData ->
                HankkeenHakemusDataResponse(
                    application.applicationData as CableReportApplicationData
                )
            is ExcavationNotificationData ->
                HankkeenHakemusDataResponse(
                    application.applicationData as ExcavationNotificationData
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
        cableReportApplicationData: CableReportApplicationData
    ) : this(
        cableReportApplicationData.name,
        cableReportApplicationData.startTime,
        cableReportApplicationData.endTime,
        cableReportApplicationData.pendingOnClient,
    )

    constructor(
        excavationNotificationData: ExcavationNotificationData
    ) : this(
        excavationNotificationData.name,
        excavationNotificationData.startTime,
        excavationNotificationData.endTime,
        excavationNotificationData.pendingOnClient
    )
}
