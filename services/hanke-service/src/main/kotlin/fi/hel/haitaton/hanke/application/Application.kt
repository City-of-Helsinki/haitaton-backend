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
