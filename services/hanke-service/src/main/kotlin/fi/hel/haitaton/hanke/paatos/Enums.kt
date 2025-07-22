package fi.hel.haitaton.hanke.paatos

import fi.hel.haitaton.hanke.allu.ApplicationStatus

enum class PaatosTyyppi {
    PAATOS,
    TOIMINNALLINEN_KUNTO,
    TYO_VALMIS;

    companion object {
        fun valueOfApplicationStatus(applicationStatus: ApplicationStatus): PaatosTyyppi =
            when (applicationStatus) {
                ApplicationStatus.DECISION -> PAATOS
                ApplicationStatus.OPERATIONAL_CONDITION -> TOIMINNALLINEN_KUNTO
                ApplicationStatus.FINISHED -> TYO_VALMIS
                else ->
                    throw IllegalArgumentException(
                        "Unsupported application status: $applicationStatus"
                    )
            }
    }
}

enum class PaatosTila {
    NYKYINEN,
    KORVATTU,
}
