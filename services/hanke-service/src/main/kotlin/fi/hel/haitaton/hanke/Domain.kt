package fi.hel.haitaton.hanke

import java.time.ZonedDateTime

/*
Domain classes
 */

data class Hanke(
        var hankeId: String,
        val name: String,
        val implStartDate: ZonedDateTime,
        val implEndDate: ZonedDateTime,
        val owner: String,
        val phase: Int)

data class HankeError(
        val errorCode: String,
        val errorMessage: String
)

class HankeNotFoundException(message: String): RuntimeException(message)
