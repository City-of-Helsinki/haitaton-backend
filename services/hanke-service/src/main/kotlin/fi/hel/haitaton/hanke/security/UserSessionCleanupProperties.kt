package fi.hel.haitaton.hanke.security

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "haitaton.security.user-session.cleanup")
data class UserSessionCleanupProperties(val interval: Duration)
