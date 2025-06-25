package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.ConstraintViolationException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun Any?.toJsonString(): String = OBJECT_MAPPER.writeValueAsString(this)

fun Any?.toJsonPrettyString(): String =
    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this)

fun String.getResource() =
    // The class here is arbitrary, could be any class. Using ClassLoader might be cleaner, but it
    // would require changing every resource file path throughout the project.
    JsonNode::class.java.getResource(this)!!

fun String.getResourceAsText(): String = this.getResource().readText(Charsets.UTF_8)

/**
 * Serializes only the main data fields; no audit fields or other irrelevant fields.
 *
 * The fields to serialize are marked with as [com.fasterxml.jackson.annotation.JsonView] with
 * [ChangeLogView] view.
 */
fun Any?.toChangeLogJsonString(): String =
    OBJECT_MAPPER.writerWithView(ChangeLogView::class.java).writeValueAsString(this)

// These marker classes are used to get a limited set of info for logging.
open class ChangeLogView

open class NotInChangeLogView

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

fun OffsetDateTime.plusMonthsPreserveEndOfMonth(months: Long): OffsetDateTime =
    this.toLocalDate().plusMonthsPreserveEndOfMonth(months).atTime(OffsetTime.now(TZ_UTC))

fun OffsetDateTime.minusMonthsPreserveEndOfMonth(months: Long): OffsetDateTime =
    this.toLocalDate().minusMonthsPreserveEndOfMonth(months).atTime(OffsetTime.now(TZ_UTC))

fun LocalDate.plusMonthsPreserveEndOfMonth(months: Long): LocalDate {
    val newDate = this.plusMonths(months)
    return if (this.dayOfMonth == this.lengthOfMonth()) {
        newDate.with(TemporalAdjusters.lastDayOfMonth())
    } else {
        newDate
    }
}

fun LocalDate.minusMonthsPreserveEndOfMonth(months: Long): LocalDate {
    val newDate = this.minusMonths(months)
    return if (this.dayOfMonth == this.lengthOfMonth()) {
        newDate.with(TemporalAdjusters.lastDayOfMonth())
    } else {
        newDate
    }
}

fun ZonedDateTime.minusMillis(millis: Long): ZonedDateTime {
    return this.minus(millis, ChronoUnit.MILLIS)
}
