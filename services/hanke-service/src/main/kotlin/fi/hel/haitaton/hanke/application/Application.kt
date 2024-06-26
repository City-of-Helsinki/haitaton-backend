package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HasId

enum class ApplicationType {
    CABLE_REPORT,
    EXCAVATION_NOTIFICATION,
}

/** Interface to enable [Application] and [CableReportWithoutHanke] handling equivalently. */
sealed interface BaseApplication {
    val applicationType: ApplicationType
    val applicationData: ApplicationData
}

data class Application(
    override val id: Long,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    override val applicationType: ApplicationType,
    override val applicationData: ApplicationData,
    val hankeTunnus: String,
) : HasId<Long>, BaseApplication {
    fun toMetadata() =
        ApplicationMetaData(
            id,
            alluid,
            alluStatus,
            applicationIdentifier,
            applicationType,
            hankeTunnus
        )
}

/** Creation of an application without hanke is enabled for cable reports. */
data class CableReportWithoutHanke(
    override val applicationType: ApplicationType,
    override val applicationData: CableReportApplicationData,
) : BaseApplication {
    fun toNewApplication(hankeTunnus: String) =
        Application(
            id = 0,
            alluid = null,
            alluStatus = null,
            applicationIdentifier = null,
            applicationType = applicationType,
            applicationData = applicationData,
            hankeTunnus = hankeTunnus,
        )
}

/** Without application data, just the identifiers and metadata. */
data class ApplicationMetaData(
    override val id: Long,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val hankeTunnus: String,
) : HasId<Long>
