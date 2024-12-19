package fi.hel.haitaton.hanke.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.Delimiter

@ConfigurationProperties(prefix = "haitaton.ad.filter")
data class AdFilterProperties(val use: Boolean, @Delimiter(";") val allowedGroups: Set<String>)
