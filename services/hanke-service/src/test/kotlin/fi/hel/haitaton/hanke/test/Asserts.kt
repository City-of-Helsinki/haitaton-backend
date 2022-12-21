package fi.hel.haitaton.hanke.test

import assertk.Assert
import assertk.assertions.isBetween
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.TemporalAmount

object Asserts {

    fun Assert<OffsetDateTime?>.isRecent(offset: TemporalAmount = Duration.ofMinutes(1)) =
        given { actual ->
            if (actual == null) return
            val now = OffsetDateTime.now()
            assertThat(actual).isBetween(now.minus(offset), now)
        }
}
