package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HasId

enum class ApplicationType {
    CABLE_REPORT,
}

data class Application(
    override val id: Long?,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: ApplicationData,
    val hankeTunnus: String,
) : HasId<Long>

/** Creation of an application without hanke is enabled for cable reports. */
data class CableReportWithoutHanke(
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: CableReportApplicationData,
)

fun CableReportWithoutHanke.toNewApplication(hankeTunnus: String) =
    Application(
        id = null,
        alluid = alluid,
        alluStatus = alluStatus,
        applicationIdentifier = applicationIdentifier,
        applicationType = applicationType,
        applicationData = applicationData,
        hankeTunnus = hankeTunnus,
    )
