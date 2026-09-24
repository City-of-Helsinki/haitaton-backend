package fi.hel.haitaton.hanke.configuration

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import fi.hel.haitaton.hanke.allu.AlluProperties
import io.mockk.mockk
import java.time.Duration
import org.junit.jupiter.api.Test

class ConfigurationTest {

    /** Allu closes keep-alive connections after 5 seconds of inactivity. */
    private val alluServerIdleTimeout: Duration = Duration.ofSeconds(5)

    private val configuration = Configuration(mockk<AlluProperties>())

    @Test
    fun `alluConnectionProvider() leaves a safety margin before Allu's own idle timeout`() {
        // A thin margin can be eaten by a GC pause or scheduler jitter, handing out a connection
        // Allu has already closed. Require a margin of at least 2 seconds.
        val margin = alluServerIdleTimeout.minus(Configuration.ALLU_MAX_IDLE_TIME)

        assertThat(margin).isGreaterThanOrEqualTo(Duration.ofSeconds(2))
    }

    @Test
    fun `alluConnectionProvider() background sweep runs well within maxIdleTime`() {
        // evictInBackground is a periodic sweep, not a per-connection timer, so it must run
        // clearly more often than maxIdleTime or idle connections could go undetected for
        // most of the idle window before being evicted.
        assertThat(Configuration.ALLU_EVICTION_INTERVAL)
            .isLessThan(Configuration.ALLU_MAX_IDLE_TIME)
    }

    @Test
    fun `alluConnectionProvider() builds a named connection provider`() {
        val provider = configuration.alluConnectionProvider()

        assertThat(provider.name()).isEqualTo("allu")
    }
}
