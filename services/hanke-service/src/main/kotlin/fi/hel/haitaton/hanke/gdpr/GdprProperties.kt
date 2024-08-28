package fi.hel.haitaton.hanke.gdpr

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "haitaton.gdpr")
data class GdprProperties(
    val disabled: Boolean,
    val issuer: String,
    val audience: String,
    val queryScope: String,
    val deleteScope: String,
)
