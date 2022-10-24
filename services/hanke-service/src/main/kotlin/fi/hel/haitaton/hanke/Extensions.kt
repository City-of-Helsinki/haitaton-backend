package fi.hel.haitaton.hanke

import java.math.BigDecimal
import java.math.RoundingMode
import javax.validation.ConstraintViolationException

fun Any?.toJsonString(): String = OBJECT_MAPPER.writeValueAsString(this)

fun Any?.toJsonPrettyString(): String =
    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)

/** Rounds a Float to one decimal. */
fun Float.roundToOneDecimal(): Float {
    return BigDecimal(this.toDouble()).setScale(1, RoundingMode.HALF_UP).toFloat()
}

internal fun ConstraintViolationException.toHankeError(default: HankeError): HankeError {
    val violation =
        constraintViolations.firstOrNull { constraintViolation ->
            constraintViolation.message.matches(HankeError.CODE_PATTERN)
        }
    return if (violation != null) {
        HankeError.valueOf(violation)
    } else {
        default
    }
}
