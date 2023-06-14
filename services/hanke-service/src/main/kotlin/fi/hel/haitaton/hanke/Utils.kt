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

    if (length != 9 || this[7] != '-') {
        return false.also { logger.warn { "Invalid format." } }
    }

    substringBefore("-")
        .map { it.digitToInt() }
        .zip(listOf(7, 9, 10, 5, 8, 4, 2))
        .sumOf { (num, multiplier) -> num * multiplier }
        .mod(11)
        .let { remainder ->
            val check = substringAfter("-").toInt()
            val result = 11 - remainder

            return when {
                remainder == 1 -> false.also { logger.warn { "Remainder not valid." } }
                (remainder == 0 && 0 == check) || result == check -> true
                else -> false.also { logger.warn { "Invalid businessId." } }
            }
        }
}
