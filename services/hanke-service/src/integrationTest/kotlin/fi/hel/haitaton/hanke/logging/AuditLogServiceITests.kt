package fi.hel.haitaton.hanke.logging

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.TestUtils
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Testing the configurations and database setup for AuditLogRepository class with a database. The
 * repositories have no additional code over the base JPARepository, so only the configs/setups get
 * indirectly tested.
 */
class AuditLogServiceITests : IntegrationTest() {

    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var auditLogService: AuditLogService

    @Test
    fun `saving audit log entry works`() {
        // Create a log entry, save it, flush, clear caches:
        val auditLogEntry =
            AuditLogEntry(
                userId = "1234-1234",
                userRole = UserRole.USER,
                operation = Operation.CREATE,
                status = Status.FAILED,
                failureDescription = "There was an error",
                objectType = ObjectType.YHTEYSTIETO,
                objectId = "333",
                objectAfter = "fake JSON"
            )
        TestUtils.addMockedRequestIp()

        val savedAuditLogEntry = auditLogService.createAll(listOf(auditLogEntry))[0]

        val id = savedAuditLogEntry.id
        // Check it is there (using something else than the repository):
        val foundAuditLogEntry = entityManager.find(AuditLogEntryEntity::class.java, id)
        assertThat(foundAuditLogEntry).isNotNull()
        assertThat(foundAuditLogEntry.id).isNotNull()
        assertThat(foundAuditLogEntry.isSent).isFalse()
        assertThat(foundAuditLogEntry.createdAt).isRecent()
        val auditLogEvent = foundAuditLogEntry.message.auditEvent
        assertThat(auditLogEvent.dateTime).isRecent()
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
