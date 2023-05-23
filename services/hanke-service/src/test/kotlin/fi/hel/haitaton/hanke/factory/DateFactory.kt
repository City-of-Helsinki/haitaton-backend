package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object DateFactory {
    /**
     * Get a static date for future start dates. The returned date is February 20th 23:45:56.000 of
     * next year.
     *
     * Either this or [getEndDatetime] will change dates if timezone handling is not done properly
     * in e.g. database.
     *
     * The date is truncated to millis to match database time granularity.
     */
    fun getStartDatetime(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC).truncatedTo(ChronoUnit.MILLIS)
    }

    /**
     * Get a static date for future end dates. The returned date is February 21st 00:12:34.000 of
     * next year.
     *
     * Either this or [getStartDatetime] will change dates if timezone handling is not done properly
     * in e.g. database.
     *
     * The date is truncated to millis to match database time granularity.
     */
    fun getEndDatetime(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC).truncatedTo(ChronoUnit.MILLIS)
    }
}
