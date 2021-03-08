package fi.hel.haitaton.hanke

import java.math.BigDecimal
import java.math.RoundingMode

fun Any?.toJsonString(): String = OBJECT_MAPPER.writeValueAsString(this)

fun Any?.toJsonPrettyString(): String = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun <T> String.asJsonResource(type: Class<T>): T =
    OBJECT_MAPPER.readValue(type.getResource(this).readText(Charsets.UTF_8), type)

/**
 * Rounds a Float to one decimals
 */
fun Float.roundToOneDecimal(): Float {
    return BigDecimal(this.toDouble()).setScale(1, RoundingMode.HALF_UP).toFloat()
}
