package fi.hel.haitaton.hanke.security

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * Custom repository interface for UserSessionEntity that handles the case where the session is
 * either new or already exists in the database. This is used to avoid the need for a separate check
 * before saving.
 */
fun interface CustomUserSessionRepository {
    fun saveIfNotExists(session: UserSessionEntity)
}

@Repository
class CustomUserSessionRepositoryImpl(
    @PersistenceContext private val entityManager: EntityManager
) : CustomUserSessionRepository {

    override fun saveIfNotExists(session: UserSessionEntity) {
        val sql =
            """
            INSERT INTO user_sessions (id, subject, session_id, created_at, expires_at, terminated)
            VALUES (:id, :subject, :session_id, :created_at, :expires_at, :terminated)
            ON CONFLICT ON CONSTRAINT uq_user_sessions_subject_sid DO NOTHING
        """
                .trimIndent()

        val query = entityManager.createNativeQuery(sql)
        query.setParameter("id", session.id)
        query.setParameter("subject", session.subject)
        query.setParameter("session_id", session.sessionId)
        query.setParameter("created_at", session.createdAt)
        query.setParameter("expires_at", session.expiresAt)
        query.setParameter("terminated", session.terminated)
        query.executeUpdate()
    }
}
