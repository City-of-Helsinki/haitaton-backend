package fi.hel.haitaton.hanke

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class HankeRepositoryITests @Autowired constructor(
        val entityManager: TestEntityManager,
        val hankeRepository: HankeRepository) {

    @Test
    fun `findByHankeTunnus returns existing hanke`() {
        // First insert one hanke to the repository:
        val hankeEntity = HankeEntity(SaveType.AUTO, "ABC-123", null, null,
                null, null, null, null, false,
                1, null, null, null, null)
        entityManager.persist(hankeEntity)
        entityManager.flush()

        // Try to find that
        val testResultEntity = hankeRepository.findByHankeTunnus("ABC-123")
        assertThat(testResultEntity).isEqualTo(hankeEntity)
    }

    // TODO: more tests

}
