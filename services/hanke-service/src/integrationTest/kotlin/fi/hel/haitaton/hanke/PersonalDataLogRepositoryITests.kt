package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.Action
import fi.hel.haitaton.hanke.logging.ChangeLogEntry
import fi.hel.haitaton.hanke.logging.PersonalDataAuditLogRepository
import fi.hel.haitaton.hanke.logging.PersonalDataChangeLogRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import javax.persistence.EntityManager

/**
 * Testing the configurations and database setup for PersonalDataXxxxLogRepository classes
 * with a database.
 * The repositories have no additional code over the base JPARepository, so
 * only the configs/setups get indirectly tested.
 */
// NOTE: using @DataJpaTest(properties = ["spring.liquibase.enabled=false"])
//  fails; it seems the way it tries to use schemas is not compatible with H2.
//  Thus, have to use this test containers -way, which uses the proper PostgreSQL.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
class PersonalDataLogRepositoryITests @Autowired constructor(
    val entityManager: EntityManager,
    val auditLogRepository: PersonalDataAuditLogRepository,
    val changeLogRepository: PersonalDataChangeLogRepository
) {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer
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

    @Test
    fun `saving audit log entry works`() {
        // Create a log entry, save it, flush, clear caches:
        val datetime = LocalDateTime.of(2020, 2, 20, 20, 20, 20)
        val audit = AuditLogEntry(datetime, "1234-1234", null, null, null, 333, Action.CREATE, false,"test create")
        val savedAudit = auditLogRepository.save(audit)
        val id = savedAudit.id
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Check it is there (using something else than the repository):
        val savedAudit2 = entityManager.find(AuditLogEntry::class.java, id)
        assertThat(savedAudit2).isNotNull()
        assertThat(savedAudit2.eventTime).isEqualTo(datetime)
        assertThat(savedAudit2.userId).isEqualTo("1234-1234")
    }

    @Test
    fun `saving change log entry works`() {
        // Create a log entry, save it, flush, clear caches:
        val datetime = LocalDateTime.of(2020, 2, 20, 20, 20, 20)
        val audit = ChangeLogEntry(datetime, 444, Action.CREATE, false, "fake JSON", "new fake JSON")
        val savedAudit = changeLogRepository.save(audit)
        val id = savedAudit.id
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Check it is there (using something else than the repository):
        val savedAudit2 = entityManager.find(ChangeLogEntry::class.java, id)
        assertThat(savedAudit2).isNotNull()
        assertThat(savedAudit2.eventTime).isEqualTo(datetime)
        assertThat(savedAudit2.oldData).isEqualTo("fake JSON")
    }

}
