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
                    createdAt = Instant.now().minusSeconds(10),
                )
            )
            assertThat(repository.findAll().single().subject).isEqualTo("subject")

            service.cleanupExpiredSessions(5)

            assertThat(repository.findAll()).isEmpty()
        }

        @Test
        fun `does not delete non-expired sessions`() {
            repository.save(
                UserSessionEntity(
                    subject = "subject",
                    sessionId = UUID.randomUUID().toString(),
                    createdAt = Instant.now().plusSeconds(60),
                )
            )
            assertThat(repository.findAll().single().subject).isEqualTo("subject")

            service.cleanupExpiredSessions(5)

            assertThat(repository.findAll().single().subject).isEqualTo("subject")
        }
    }

    @Nested
    inner class SaveSessionIfNotExists {

        @Test
        fun `saves non-existing session`() {
            service.saveSessionIfNotExists(
                subject = "subject",
                sessionId = UUID.randomUUID().toString(),
                createdAt = Instant.now(),
            )

            assertThat(repository.findAll().single().subject).isEqualTo("subject")
        }

        @Test
        fun `does not save existing session`() {
            val sessionId = UUID.randomUUID().toString()
            repository.save(
                UserSessionEntity(
                    subject = "subject",
                    sessionId = sessionId,
                    createdAt = Instant.now(),
                )
            )
            assertThat(repository.findAll().single().subject).isEqualTo("subject")

            service.saveSessionIfNotExists(
                subject = "subject",
                sessionId = sessionId,
                createdAt = Instant.now(),
            )

            assertThat(repository.findAll().single().subject).isEqualTo("subject")
        }
    }
}
