package fi.hel.haitaton.data

import java.time.ZonedDateTime


data class Hanke(var hankeId: String, val name: String, val implStartDate: ZonedDateTime, val implEndDate: ZonedDateTime, val owner: String, val phase: Int)

