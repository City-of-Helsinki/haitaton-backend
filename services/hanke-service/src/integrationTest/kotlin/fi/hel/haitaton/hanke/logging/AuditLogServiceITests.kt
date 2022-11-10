package fi.hel.haitaton.hanke.logging

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.support.expected
import assertk.assertions.support.show
import fi.hel.haitaton.hanke.DatabaseTest
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.TemporalAmount
import javax.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Testing the configurations and database setup for AuditLogRepository class with a database. The
 * repositories have no additional code over the base JPARepository, so only the configs/setups get
 * indirectly tested.
 */
// NOTE: using @DataJpaTest(properties = ["spring.liquibase.enabled=false"])
//  fails; it seems the way it tries to use schemas is not compatible with H2.
//  Thus, have to use this test containers -way, which uses the proper PostgreSQL.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
class AuditLogServiceITests : DatabaseTest() {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var auditLogService: AuditLogService

    fun Assert<OffsetDateTime?>.isRecent(offset: TemporalAmount) = given { actual ->
        if (actual == null) return
        val now = OffsetDateTime.now()
        if (actual.isBefore(now) && actual.isAfter(now.minus(offset))) return
        expected("after:${show(now.minus(offset))} but was:${show(actual)}")
    }

    fun Assert<LocalDateTime?>.isRecentLocal(offset: TemporalAmount) = given { actual ->
        if (actual == null) return
        val now = LocalDateTime.now()
        if (actual.isBefore(now) && actual.isAfter(now.minus(offset))) return
        expected("after:${show(now.minus(offset))} but was:${show(actual)}")
    }

    @Test
    fun `saving audit log entry works`() {
        // Create a log entry, save it, flush, clear caches:
        val auditLogEntry =
            AuditLogEntry(
                userId = "1234-1234",
                userRole = UserRole.USER,
                ipAddress = "127.0.0.1",
                operation = Operation.CREATE,
                status = Status.FAILED,
                failureDescription = "There was an error",
                objectType = ObjectType.YHTEYSTIETO,
                objectId = "333",
                objectAfter = "fake JSON"
            )
        val savedAuditLogEntry = auditLogService.saveAll(listOf(auditLogEntry))[0]
        val id = savedAuditLogEntry.id
        // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.flush()
        // Ensure the original entity is no longer in Hibernate's 1st level cache
        entityManager.clear()

        // Check it is there (using something else than the repository):
        val foundAuditLogEntry = entityManager.find(AuditLogEntryEntity::class.java, id)
        assertThat(foundAuditLogEntry).isNotNull()
        assertThat(foundAuditLogEntry.id).isNotNull()
        assertThat(foundAuditLogEntry.isSent).isFalse()
        assertThat(foundAuditLogEntry.createdAt).isRecentLocal(Duration.ofMinutes(1))
        val auditLogEvent = foundAuditLogEntry.message.auditEvent
        assertThat(auditLogEvent.dateTime).isRecent(Duration.ofMinutes(1))
        assertThat(auditLogEvent.appVersion).isEqualTo("1")
        assertThat(auditLogEvent.operation).isEqualTo(Operation.CREATE)
        assertThat(auditLogEvent.status).isEqualTo(Status.FAILED)
        assertThat(auditLogEvent.failureDescription).isEqualTo("There was an error")
        assertThat(auditLogEvent.actor.userId).isEqualTo("1234-1234")
        assertThat(auditLogEvent.actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvent.actor.ipAddress).isEqualTo("127.0.0.1")
        assertThat(auditLogEvent.target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvent.target.id).isEqualTo("333")
        assertThat(auditLogEvent.target.objectAfter).isEqualTo("fake JSON")
        assertThat(auditLogEvent.target.objectBefore).isNull()
    }
}
