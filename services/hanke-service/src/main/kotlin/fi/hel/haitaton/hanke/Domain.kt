package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.time.ZonedDateTime
import javax.validation.ConstraintViolation

/*
Domain classes
 */

data class Hanke(
        var hankeId: String?,
        var isYKTHanke: Boolean?,
        var name: String?,
        var startDate: ZonedDateTime?,
        var endDate: ZonedDateTime?,
        val owner: String,
        var phase: Int?)

@JsonSerialize(using = HankeErrorSerializer::class)
enum class HankeError(
        val errorMessage: String) {
    HAI1001("Hanke not found"),
    HAI1011("Invalid Hanke geometry"),
    HAI1012("Internal error while saving Hanke geometry"),
    HAI1013("Invalid coordinate system"),
    HAI1014("Internal error while loading Hanke geometry"),
    HAI1015("Hanke geometry not found");

    val errorCode: String
        get() = name

    companion object {
        fun valueOf(violation: ConstraintViolation<*>): HankeError {
            return valueOf(violation.message.split(":")[0])
        }
    }

    override fun toString(): String {
        return "$name - $errorMessage"
    }
}

class HankeNotFoundException(val hankeId: String? = null) : RuntimeException(HankeError.HAI1001.errorMessage)
