package fi.hel.haitaton.hanke.security

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSessionRepository :
    JpaRepository<UserSessionEntity, UUID>, CustomUserSessionRepository {
    @Query(
        "SELECT CASE WHEN COUNT(us) > 0 THEN true ELSE false END " +
            "FROM UserSessionEntity us WHERE us.sessionId = :sessionId AND us.terminated = false"
    )
    fun existsBySessionIdAndNotTerminated(@Param("sessionId") sessionId: String): Boolean

    @Modifying
    @Query(
        "UPDATE UserSessionEntity us SET us.terminated = true " +
            "WHERE us.sessionId = :sessionId AND us.terminated = false"
    )
    fun terminateBySessionId(@Param("sessionId") sessionId: String): Int

    @Modifying
    @Query(
        "UPDATE UserSessionEntity us SET us.terminated = true " +
            "WHERE us.subject = :subject AND us.terminated = false"
    )
    fun terminateBySubject(@Param("subject") subject: String): Int

    @Modifying
    @Query("DELETE FROM UserSessionEntity us WHERE us.expiresAt < :now")
    fun deleteAllByExpiration(@Param("now") now: Instant): Int
}
