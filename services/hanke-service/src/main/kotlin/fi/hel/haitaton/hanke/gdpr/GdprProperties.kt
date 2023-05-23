package fi.hel.haitaton.hanke.gdpr

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties(prefix = "haitaton.gdpr")
@ConstructorBinding
data class GdprProperties(
    val disabled: Boolean,
    val issuer: String,
    val audience: String,
    val authorizationField: String,
    val queryScope: String,
    val deleteScope: String,
)
