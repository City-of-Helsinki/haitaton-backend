package fi.hel.haitaton.hanke.security

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.IntegrationTest
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class UserSessionServiceITest(
    @Autowired private val service: UserSessionService,
    @Autowired private val repository: UserSessionRepository,
) : IntegrationTest() {

    @Nested
    inner class CleanupExpiredSessions {

        @Test
        fun `deletes expired sessions`() {
            repository.save(
                UserSessionEntity(
                    subject = "subject",
                    sessionId = UUID.randomUUID().toString(),
                    createdAt = Instant.now(),
                    expiresAt = Instant.now().minusSeconds(10),
                )
            )
            assertThat(repository.findAll().single().subject).isEqualTo("subject")

            service.cleanupExpiredSessions()

            assertThat(repository.findAll()).isEmpty()
        }

        @Test
        fun `does not delete non-expired sessions`() {
            repository.save(
                UserSessionEntity(
                    subject = "subject",
                    sessionId = UUID.randomUUID().toString(),
                    createdAt = Instant.now(),
                    expiresAt = Instant.now().plusSeconds(60),
                )
            )
            assertThat(repository.findAll().single().subject).isEqualTo("subject")

            service.cleanupExpiredSessions()

            assertThat(repository.findAll().single().subject).isEqualTo("subject")
        }
    }

    @Nested
    inner class ValidateAndSaveSession {

        @Test
        fun `returns true when session exists and is not terminated`() {
            val sessionId = UUID.randomUUID().toString()
            val expiresAt = Instant.now().plusSeconds(3600)
            repository.save(
                UserSessionEntity(
                    subject = "subject",
                    sessionId = sessionId,
                    createdAt = Instant.now(),
                    expiresAt = expiresAt,
                    terminated = false,
                )
            )

            val isValid =
                service.validateAndSaveSession("subject", sessionId, Instant.now(), expiresAt)

            assertThat(isValid).isEqualTo(true)
        }

        @Test
        fun `returns true and saves new session`() {
            val sessionId = UUID.randomUUID().toString()
            // Truncate to microseconds to match PostgreSQL TIMESTAMP precision
            val expiresAt =
                Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            assertThat(repository.findAll()).isEmpty()

            val isValid =
                service.validateAndSaveSession("subject", sessionId, Instant.now(), expiresAt)

            assertThat(isValid).isEqualTo(true)
            val saved = repository.findAll().single()
            assertThat(saved.sessionId).isEqualTo(sessionId)
            assertThat(saved.expiresAt).isEqualTo(expiresAt)
            assertThat(saved.terminated).isEqualTo(false)
        }

        @Test
        fun `returns false when session is terminated`() {
            val sessionId = UUID.randomUUID().toString()
            val expiresAt = Instant.now().plusSeconds(3600)
            repository.save(
                UserSessionEntity(
                    subject = "subject",
                    sessionId = sessionId,
                    createdAt = Instant.now(),
                    expiresAt = expiresAt,
                    terminated = true,
                )
            )

            val isValid =
                service.validateAndSaveSession("subject", sessionId, Instant.now(), expiresAt)

            assertThat(isValid).isEqualTo(false)
        }
    }
}
