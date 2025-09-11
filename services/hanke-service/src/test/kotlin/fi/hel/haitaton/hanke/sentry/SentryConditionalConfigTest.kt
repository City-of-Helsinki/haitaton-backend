package fi.hel.haitaton.hanke.sentry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment

class SentryConditionalConfigTest {

    @Test
    fun `Sets sentry enabled false when HAITATON_SENTRY_ENVIRONMENT is none`() {
        val env = MockEnvironment().withProperty("HAITATON_SENTRY_ENVIRONMENT", "none")
        val disabler = ConditionalSentryDisabler()

        disabler.postProcessEnvironment(env, SpringApplication())

        assertThat(env.getProperty("sentry.enabled")).isEqualTo("false")
    }

    @Test
    fun `Does not set sentry enabled false when HAITATON_SENTRY_ENVIRONMENT is prod`() {
        val env = MockEnvironment().withProperty("HAITATON_SENTRY_ENVIRONMENT", "prod")
        val disabler = ConditionalSentryDisabler()

        disabler.postProcessEnvironment(env, SpringApplication())

        assertThat(env.getProperty("sentry.enabled")).isNull()
    }
}

