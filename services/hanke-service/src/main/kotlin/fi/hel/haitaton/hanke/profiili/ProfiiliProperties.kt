package fi.hel.haitaton.hanke.profiili

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "haitaton.profiili-api")
data class ProfiiliProperties(
    val graphQlUrl: String,
    val audience: String,
)
