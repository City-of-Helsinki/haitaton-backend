package fi.hel.haitaton.hanke

import java.time.ZonedDateTime
import javax.validation.ConstraintViolation

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
) {
    constructor(violation: ConstraintViolation<*>) : this(
            violation.message.split(":")[0],
            violation.message.split(":")[1]
    )
}

class HankeNotFoundException(message: String): RuntimeException(message)
