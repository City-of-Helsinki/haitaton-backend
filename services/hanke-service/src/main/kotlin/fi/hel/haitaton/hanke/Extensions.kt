package fi.hel.haitaton.hanke

import kotlin.math.roundToInt

fun Any?.toJsonString(): String = OBJECT_MAPPER.writeValueAsString(this)

fun Any?.toJsonPrettyString(): String = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun <T> String.asJsonResource(type: Class<T>): T =
    OBJECT_MAPPER.readValue(type.getResource(this).readText(Charsets.UTF_8), type)

/**
 * Rounds a Float to a number of given decimals
 */
fun Float.round(decimals: Int): Float {
    var multiplier = 1.0f
    repeat(decimals) { multiplier *= 10 }
    return (this * multiplier).roundToInt() / multiplier
}
