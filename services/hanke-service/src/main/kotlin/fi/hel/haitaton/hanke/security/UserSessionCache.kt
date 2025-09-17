package fi.hel.haitaton.hanke.security

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Cache for user sessions to track their expiration. Using a cache to avoid frequent database
 * lookups.
 */
@Component
class UserSessionCache {

    private val cache: ConcurrentMap<String, Instant> = ConcurrentHashMap()
    private val ttlSeconds = 3600L // 1 hour

    fun isSessionKnown(key: String): Boolean {
        val now = Instant.now()
        val expiration = cache[key]

        return if (expiration != null && expiration.isAfter(now)) {
            true
        } else {
            cache.remove(key)
            false
        }
    }

    fun markSessionAsSeen(key: String) {
        val expiresAt = Instant.now().plusSeconds(ttlSeconds)
        cache[key] = expiresAt
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000) // 10 minutes
    fun cleanupExpired() {
        val now = Instant.now()
        cache.entries.removeIf { it.value.isBefore(now) }
    }
}
