package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.BusinessId
import java.time.LocalDateTime
import java.time.ZonedDateTime
import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder

private val logger = KotlinLogging.logger {}

/** Returns the current time in UTC, with time zone info. */
fun getCurrentTimeUTC(): ZonedDateTime {
    return ZonedDateTime.now(TZ_UTC)
}

/** Returns the current time in UTC, without time zone info (i.e. LocalTime instance). */
fun getCurrentTimeUTCAsLocalTime(): LocalDateTime {
    return ZonedDateTime.now(TZ_UTC).toLocalDateTime()
}

fun currentUserId(): String = SecurityContextHolder.getContext().authentication.name

/**
 * Valid businessId (y-tunnus) requirements:
 * 1. format NNNNNNN-T, where N = sequence number and T = check number.
 * 2. documentation of check mark calculation:
 * ```
 *     - https://www.vero.fi/globalassets/tietoa-verohallinnosta/ohjelmistokehittajille/yritys--ja-yhteis%C3%B6tunnuksen-ja-henkil%C3%B6tunnuksen-tarkistusmerkin-tarkistuslaskenta.pdf
 * ```
 *
 * Only verifies that the id is of valid form. It does not guarantee that it actually exists.
 */
fun BusinessId.isValidBusinessId(): Boolean {
    logger.info { "Verifying businessId: $this" }

    if (length != 9 || substring(0, 7).any { !it.isDigit() } || this[7] != '-') {
        logger.warn { "Invalid format." }
        return false
    }

    val checkDigit = last().toString().toIntOrNull()
    if (checkDigit == null) {
        logger.warn { "Invalid check digit." }
        return false
    }

    substringBefore("-")
        .map { it.digitToInt() }
        .zip(listOf(7, 9, 10, 5, 8, 4, 2))
        .sumOf { (num, multiplier) -> num * multiplier }
        .mod(11)
        .let { remainder ->
            val calculatedCheck = if (remainder == 0) 0 else 11 - remainder

            if (remainder == 1) {
                logger.warn { "Remainder not valid." }
                return false
            }

            if (calculatedCheck == checkDigit) {
                return true
            }

            logger.warn { "Invalid businessId" }
            return false
        }
}
