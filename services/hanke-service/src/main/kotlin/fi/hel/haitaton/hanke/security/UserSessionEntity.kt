package fi.hel.haitaton.hanke.security

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_sessions")
class UserSessionEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var subject: String,
    @Column(name = "session_id") var sessionId: String? = null,
    @Column(name = "created_at") var createdAt: Instant = Instant.now(),
    @Column(name = "expires_at") var expiresAt: Instant? = null,
    var terminated: Boolean = false,
)
