package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.EndpointDisabledException
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "haitaton")
data class FeatureFlags(val features: Map<Feature, Boolean>) {

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
