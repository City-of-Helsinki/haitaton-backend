package fi.hel.haitaton.hanke.sentry

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Disables Sentry entirely when HAITATON_SENTRY_ENVIRONMENT is `none` (default).
 *
 * We do this very early in the Spring Environment lifecycle so that the Sentry starter sees the
 * property `sentry.enabled=false` and skips initialization. Any existing static
 * Sentry.captureException(...) calls will effectively be no-ops because the SDK isn't started.
 */
class ConditionalSentryDisabler : EnvironmentPostProcessor, Ordered {

    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        val value = environment.getProperty("HAITATON_SENTRY_ENVIRONMENT", "none")
        if (value.equals("none", ignoreCase = true)) {
            // Highest precedence so it can be overridden explicitly if ever needed.
            val props = mapOf("sentry.enabled" to "false")
            environment.propertySources.addFirst(
                MapPropertySource("conditionalSentry", props)
            )
        }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE
}

