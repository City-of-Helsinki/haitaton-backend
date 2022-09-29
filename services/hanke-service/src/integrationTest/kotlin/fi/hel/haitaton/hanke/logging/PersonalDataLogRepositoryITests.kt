package fi.hel.haitaton.hanke.logging

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.support.expected
import assertk.assertions.support.show
import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import java.time.Duration
import java.time.OffsetDateTime
import java.time.temporal.TemporalAmount
import javax.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
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
class PersonalDataLogRepositoryITests
@Autowired
constructor(
    val entityManager: EntityManager,
    val auditLogRepository: AuditLogRepository,
) {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer =
            HaitatonPostgreSQLContainer()
                .withExposedPorts(5433) // use non-default port
                .withPassword("test")
                .withUsername("test")

        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", container::getJdbcUrl)
            registry.add("spring.datasource.username", container::getUsername)
            registry.add("spring.datasource.password", container::getPassword)
            registry.add("spring.liquibase.url", container::getJdbcUrl)
            registry.add("spring.liquibase.user", container::getUsername)
            registry.add("spring.liquibase.password", container::getPassword)
        }
    }

    fun Assert<OffsetDateTime?>.isRecent(offset: TemporalAmount) = given { actual ->
        if (actual == null) return
        val now = OffsetDateTime.now()
        if (actual.isBefore(now) && actual.isAfter(now.minus(offset))) return
        expected("after:${show(now.minus(offset))} but was:${show(actual)}")
    }

    @Test
    fun `saving audit log entry works`() {
        // Create a log entry, save it, flush, clear caches:
        val auditLogEntry =
            AuditLogEntry(
                userId = "1234-1234",
                action = Action.CREATE,
                status = Status.SUCCESS,
                objectType = ObjectType.YHTEYSTIETO,
                objectId = 333,
                objectAfter = "fake JSON"
            )
        val savedAuditLogEntry = auditLogRepository.save(auditLogEntry)
        val id = savedAuditLogEntry.id
        // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.flush()
        // Ensure the original entity is no longer in Hibernate's 1st level cache
        entityManager.clear()

        // Check it is there (using something else than the repository):
        val foundAuditLogEntry = entityManager.find(AuditLogEntry::class.java, id)
        assertThat(foundAuditLogEntry).isNotNull()
        assertThat(foundAuditLogEntry.eventTime).isRecent(Duration.ofMinutes(1))
        assertThat(foundAuditLogEntry.userId).isEqualTo("1234-1234")
        assertThat(foundAuditLogEntry.action).isEqualTo(Action.CREATE)
        assertThat(foundAuditLogEntry.status).isEqualTo(Status.SUCCESS)
        assertThat(foundAuditLogEntry.objectType).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(foundAuditLogEntry.objectId).isEqualTo(333)
        assertThat(foundAuditLogEntry.objectAfter).isEqualTo("fake JSON")
    }
}
