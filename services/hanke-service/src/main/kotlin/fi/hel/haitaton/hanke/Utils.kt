package fi.hel.haitaton.hanke

import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.kotlinModule
import fi.hel.haitaton.hanke.configuration.LngLatAltJackson3Deserializer
import fi.hel.haitaton.hanke.configuration.LngLatAltJackson3Serializer
import fi.hel.haitaton.hanke.domain.HasId
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import kotlin.reflect.KProperty1
import mu.KotlinLogging
import org.geojson.LngLatAlt
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

private val logger = KotlinLogging.logger {}

private val businessIdRegex = "^(\\d{7})-(\\d)\$".toRegex()
private val businessIdMultipliers = listOf(7, 9, 10, 5, 8, 4, 2)

fun createObjectMapper(): JsonMapper =
    JsonMapper.builder()
        .addModule(kotlinModule())
        .addModule(
            SimpleModule()
                .addSerializer(LngLatAlt::class.java, LngLatAltJackson3Serializer())
                .addDeserializer(LngLatAlt::class.java, LngLatAltJackson3Deserializer())
        )
        // Jackson 3 defaults this to disabled (opt-in views), unlike Jackson 2. The codebase's
        // ChangeLogView/NotInChangeLogView pattern (see toChangeLogJsonString()) assumes Jackson
        // 2's opt-out semantics: fields with no @JsonView annotation must still be included.
        .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        .build()

/**
 * Helper for mapping and sorting data to existing collections.
 *
 * Example usage:
 * ```
 * mergeDataInto(hanke.alueet, entity.listOfHankeAlueet) { source, target ->
 *    copyNonNullHankealueFieldsToEntity(hanke, source, target)
 * }
 * ```
 * - Transforms data from hanke.alueet into entity.listOfHankeAlueet
 * - Does the transformation with the last lambda
 *
 * Source list is not modified. Target container is sorted by order of source container.
 *
 * Converter takes additional parameter for existing Target object in case it exists in target
 * collection.
 */
fun <ID, Source : HasId<ID>, Target : HasId<ID & Any>> mergeDataInto(
    source: List<Source>,
    target: MutableList<Target>,
    converterFn: (Source, Target?) -> Target,
) {
    // Existing data is collected for mapping
    val targetMap = target.associateBy { it.id }

    // Target is overwritten with merged and new data from source
    target.clear()
    source.forEach { target.add(converterFn(it, targetMap[it.id])) }
}

/**
 * Returns the current time in UTC, with time zone info.
 *
 * Truncated to microseconds, since that's the database precision.
 */
fun getCurrentTimeUTC(): ZonedDateTime = ZonedDateTime.now(TZ_UTC).truncatedTo(ChronoUnit.MICROS)

fun LocalDateTime.zonedDateTime(): ZonedDateTime = ZonedDateTime.of(this, TZ_UTC)

/**
 * Returns the current time in UTC, without time zone info (i.e. LocalTime instance).
 *
 * Truncated to microseconds, since that's the database precision.
 */
fun getCurrentTimeUTCAsLocalTime(): LocalDateTime = getCurrentTimeUTC().toLocalDateTime()

/**
 * Don't call this outside controllers. If the code is called from scheduled methods, this will
 * throw a NullPointerException.
 */
fun currentUserId(): String = SecurityContextHolder.getContext().userId()

fun SecurityContext.userId(): String = authentication!!.name

/**
 * Valid business id (y-tunnus) requirements:
 * 1. format NNNNNNN-T, where N = sequence number and T = check number.
 * 2. documentation of check mark calculation:
 * ```
 *     - https://www.vero.fi/globalassets/tietoa-verohallinnosta/ohjelmistokehittajille/yritys--ja-yhteis%C3%B6tunnuksen-ja-henkil%C3%B6tunnuksen-tarkistusmerkin-tarkistuslaskenta.pdf
 * ```
 *
 * Only verifies that the id is of valid form. It does not guarantee that it actually exists.
 */
fun String?.isValidBusinessId(): Boolean {
    logger.info { "Verifying business id: $this" }

    if (this == null) {
        logger.warn { "Business id is null." }
        return false
    }

    val matchResult = businessIdRegex.find(this)
    if (matchResult == null) {
        logger.warn { "Invalid format." }
        return false
    }

    val (sequenceNumber, checkDigit) = matchResult.destructured

    val calculatedCheck =
        sequenceNumber
            .map { it.digitToInt() }
            .zip(businessIdMultipliers)
            .sumOf { (num, multiplier) -> num * multiplier }
            .mod(11)
            .let { remainder ->
                when (remainder) {
                    1 -> {
                        logger.warn { "Remainder not valid." }
                        return false
                    }
                    0 -> 0
                    else -> 11 - remainder
                }
            }

    return if (calculatedCheck == checkDigit.toInt()) {
        true
    } else {
        logger.warn { "Check digit doesn't match." }
        false
    }
}

/**
 * OVT field can contain either an OVT code or an account number. It is only required to be at least
 * 12 characters long.
 */
fun String?.isValidOVT(): Boolean {
    logger.info { "Verifying OVT: $this" }

    if (this == null) {
        logger.warn { "OVT is null." }
        return false
    }

    return this.length >= 12
}

/** Helper function to calculate the duration between two dates, inclusive. */
fun daysBetween(haittaAlkuPvm: Temporal?, haittaLoppuPvm: Temporal?): Int? =
    if (haittaAlkuPvm != null && haittaLoppuPvm != null) {
        daysBetween(haittaAlkuPvm, haittaLoppuPvm)
    } else {
        null
    }

/** Helper function to calculate the duration between two dates, inclusive. */
fun daysBetween(haittaAlkuPvm: Temporal, haittaLoppuPvm: Temporal): Int =
    ChronoUnit.DAYS.between(haittaAlkuPvm, haittaLoppuPvm).toInt() + 1

/** Check whether a property has changed between two objects. */
fun <T : Any> T.checkChange(property: KProperty1<T, Any?>, second: T): String? =
    if (property.get(this) != property.get(second)) {
        property.name
    } else null
