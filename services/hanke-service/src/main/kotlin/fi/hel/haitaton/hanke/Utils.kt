package fi.hel.haitaton.hanke

import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.springframework.security.core.context.SecurityContextHolder

/** Returns the current time in UTC, with time zone info. */
fun getCurrentTimeUTC(): ZonedDateTime {
    return ZonedDateTime.now(TZ_UTC)
}

/** Returns the current time in UTC, without time zone info (i.e. LocalTime instance). */
fun getCurrentTimeUTCAsLocalTime(): LocalDateTime {
    return ZonedDateTime.now(TZ_UTC).toLocalDateTime()
}

fun currentUserId(): String = SecurityContextHolder.getContext().authentication.name
