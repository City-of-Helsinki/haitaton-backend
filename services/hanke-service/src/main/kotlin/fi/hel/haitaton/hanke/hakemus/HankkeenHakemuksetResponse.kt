package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
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
        application: ApplicationEntity
    ) : this(
        application.id!!,
        application.alluid,
        application.alluStatus,
        application.applicationIdentifier,
        application.applicationType,
        HankkeenHakemusDataResponse(
            when (application.applicationData) {
                is CableReportApplicationData ->
                    application.applicationData as CableReportApplicationData
            },
        ),
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
}
