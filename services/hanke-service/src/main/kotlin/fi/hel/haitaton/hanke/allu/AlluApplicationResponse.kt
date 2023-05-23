package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime

/** Response for GET /v2/applications/{id} from Allu. */
data class AlluApplicationResponse(
    val id: Int,
    val name: String,
    val applicationId: String,
    val status: ApplicationStatus,
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val owner: AlluUser?,
    val kindsWithSpecifiers: Map<String, List<ApplicationKind>>,
    val terms: String?,
    val customerReference: String?,
    val surveyRequired: Boolean,
)

data class AlluUser(
    val name: String,
    val title: String,
)
