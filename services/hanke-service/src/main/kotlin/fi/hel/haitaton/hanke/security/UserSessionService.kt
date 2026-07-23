package fi.hel.haitaton.hanke.security

import java.time.Instant
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class UserSessionService(private val repository: UserSessionRepository) {

    @Transactional
    fun cleanupExpiredSessions() {
        val now = Instant.now()
        val count = repository.deleteAllByExpiration(now)
        logger.info { "Deleted $count expired user sessions (expiresAt < $now)" }
    }

    /**
     * Validates and saves user session.
     *
     * This method handles both existing sessions and new session registration. It performs a
     * three-step validation process:
     * 1. Check if session already exists (common case for subsequent requests)
     * 2. If not, attempt to save it as a new session (first request from user)
     * 3. Verify the session exists after save attempt
     *
     * The verification step is critical because it catches the following scenarios:
     * - Session was deleted by backchannel logout between the initial check and save attempt (race
     *   condition in distributed environment)
     * - Database constraint prevented the save (though saveIfNotExists uses ON CONFLICT DO NOTHING)
     * - Any other unexpected failure that prevented the session from being persisted
     *
     * In a multi-instance deployment, it's possible (though rare) that a backchannel logout occurs
     * between steps 1 and 2, deleting the session before we save it. The verification ensures we
     * detect this and reject the request.
     *
     * @param subject the user's subject identifier from JWT
     * @param sessionId the session ID from JWT sid claim
     * @param createdAt the session creation timestamp from JWT issuedAt claim
     * @param expiresAt the session expiration timestamp from JWT expiresAt claim
     * @return true if session is valid, false if session was terminated or could not be saved
     */
    @Transactional
    fun validateAndSaveSession(
        subject: String,
        sessionId: String,
        createdAt: Instant?,
        expiresAt: Instant?,
    ): Boolean {
        // Check if session exists and is not terminated (common case for subsequent requests)
        if (repository.existsBySessionIdAndNotTerminated(sessionId)) {
            return true
        }

        // Session doesn't exist or is terminated - try to save it as a new session (first request)
        val session =
            UserSessionEntity(
                subject = subject,
                sessionId = sessionId,
                createdAt = createdAt ?: Instant.now(),
                expiresAt = expiresAt,
                terminated = false,
            )
        repository.saveIfNotExists(session)

        // Verify the session exists and is not terminated after save attempt.
        // If this returns false, it means either:
        // 1. The session was terminated via backchannel logout during this method execution
        // 2. A database constraint or other issue prevented the save
        // 3. A terminated session exists with this ID and saveIfNotExists didn't insert
        // In any case, we must reject the request as the session is not valid.
        return repository.existsBySessionIdAndNotTerminated(sessionId)
    }
}
