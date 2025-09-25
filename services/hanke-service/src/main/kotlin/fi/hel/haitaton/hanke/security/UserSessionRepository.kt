package fi.hel.haitaton.hanke.security

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSessionRepository :
    JpaRepository<UserSessionEntity, UUID>, CustomUserSessionRepository {
    fun deleteBySessionId(sessionId: String): UserSessionEntity?

    fun deleteBySubject(subject: String): UserSessionEntity?

    @Modifying
    @Query("DELETE FROM UserSessionEntity us WHERE us.createdAt < :expirationDateTime")
    fun deleteAllByExpiration(@Param("expirationDateTime") expirationDateTime: Instant): Int
}
