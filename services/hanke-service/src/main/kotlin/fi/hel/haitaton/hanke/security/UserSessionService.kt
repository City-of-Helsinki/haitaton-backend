package fi.hel.haitaton.hanke.security

import java.time.Instant
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class UserSessionService(private val repository: UserSessionRepository) {

    @Transactional
    fun cleanupExpiredSessions(seconds: Long) {
        val now = Instant.now()
        val expiration = now.minusSeconds(seconds)
        val count = repository.deleteAllByExpiration(expiration)
        logger.info { "Deleted $count expired user sessions older than $expiration" }
    }

    @Transactional
    fun saveSessionIfNotExists(subject: String, sessionId: String, createdAt: Instant?) {
        val session =
            UserSessionEntity(
                subject = subject,
                sessionId = sessionId,
                createdAt = createdAt ?: Instant.now(),
            )
        repository.saveIfNotExists(session)
    }
}
