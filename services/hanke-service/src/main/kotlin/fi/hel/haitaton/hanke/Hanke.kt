package fi.hel.haitaton.hanke

import java.time.ZonedDateTime

/**
 * When creating, only owner is mandatory.
 */
data class Hanke(
        var hankeId: String?,
        var isYKTHanke: Boolean?,
        var name: String?,
        var startDate: ZonedDateTime?,
        var endDate: ZonedDateTime?,
        val owner: String,
        var phase: Int?)

