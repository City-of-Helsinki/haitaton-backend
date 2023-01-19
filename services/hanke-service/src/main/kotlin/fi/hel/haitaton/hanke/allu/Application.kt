package fi.hel.haitaton.hanke.allu

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
    val applicationData: ApplicationData
) : HasId<Long>
