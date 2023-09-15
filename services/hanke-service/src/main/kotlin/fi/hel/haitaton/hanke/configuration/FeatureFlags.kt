package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.EndpointDisabledException
import mu.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class FeatureService(private val featureFlags: FeatureFlags) {
    fun isEnabled(feature: String): Boolean {
        featureFlags.ensureEnabled(Feature.valueOf(feature))
        return true
    }
}

@ConfigurationProperties(prefix = "haitaton")
data class FeatureFlags(val features: Map<Feature, Boolean>) {

    @EventListener(ApplicationReadyEvent::class)
    fun logWarning() {
        logger.info { "Listing feature flags:" }
        features.forEach { logger.info { "Feature ${it.key} is ${it.value}" } }
    }

    /** Disabled by default, if not in application.properties. */
    private fun isEnabled(feature: Feature): Boolean = features.getOrDefault(feature, false)

    fun isDisabled(feature: Feature) = !isEnabled(feature)

    /**
     * Throws an exception if the feature is not enabled.
     *
     * Disabled by default, if not in application.properties.
     */
    fun ensureEnabled(feature: Feature) {
        if (!isEnabled(feature)) {
            throw EndpointDisabledException()
        }
    }
}

enum class Feature {
    HANKE_EDITING,
    USER_MANAGEMENT,
}
