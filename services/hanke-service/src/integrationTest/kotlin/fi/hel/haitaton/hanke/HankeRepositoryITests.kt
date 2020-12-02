package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

/**
 * Testing the HankeRepository with a database.
 */
@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class HankeRepositoryITests @Autowired constructor(
        val entityManager: TestEntityManager,
        val hankeRepository: HankeRepository) {

    @Test
    fun `findByHankeTunnus returns existing hanke`() {
        // First insert one hanke to the repository (using entityManager directly):
        val hankeEntity = HankeEntity(SaveType.AUTO, "ABC-123", null, null,
                null, null, null, null, false,
                1, null, null, null, null)
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Try to find that (using our Repository implementation):
        val testResultEntity = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(testResultEntity).isEqualTo(hankeEntity)
    }

    @Test
    fun `findByHankeTunnus does not return anything for non-existing hanke`() {
        // First insert one hanke to the repository (using entityManager directly):
        val hankeEntity = HankeEntity(SaveType.AUTO, "ABC-123", null, null,
                null, null, null, null, false,
                1, null, null, null, null)
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Check that non-existing hanke returns nothing:
        val testResultEntity = hankeRepository.findByHankeTunnus("NOT-000")
        assertThat(testResultEntity).isNull()
    }

    // TODO: more tests (when more functions appear)

}
