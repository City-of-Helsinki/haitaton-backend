package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.LocalDateTime

/**
 * Testing the TormaysTarkasteluTulosRepository with a database.
 */
@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class TormaysTarkasteluTulosRepositoryITests @Autowired constructor(
        val entityManager: TestEntityManager,
        val tormaystarkasteluTulosRepository: TormaystarkasteluTulosRepository,
        val hankeRepository: HankeRepository
) {

    val DATETIME = LocalDateTime.of(2020, 2, 20, 20, 20, 20)
    val TEST_INDEX_VALUE = 3.3f
    val TEST_HANKE_TUNNUS = "TEST-123"

    @Test
    fun `findByHankeId returns existing tulos and hanke`() {
        // Create parent Hanke (with mostly null data), and a tulos connected with it, persist both:
        val hankeEntity = persistHanke(HankeEntity(hankeTunnus = TEST_HANKE_TUNNUS))
        val hankeId = hankeEntity.id!!
        val tulosEntity = prepareTulos(hankeEntity)
        entityManager.persist(tulosEntity)
        entityManager.flush()
        val tulosId = tulosEntity.id

        // Try to find that (using our Repository implementation):
        val testResultEntityList = tormaystarkasteluTulosRepository.findByHankeId(hankeId)
        val testResultEntity = testResultEntityList[0]
        assertThat(testResultEntity).isNotNull()
        assertThat(testResultEntity.id).isEqualTo(tulosId)
    }

    @Test
    fun `findByHankeId does not return anything for non-existing hanke`() {
        // Create parent Hanke (with mostly null data), and a tulos connected with it, persist both:
        val hankeEntity = persistHanke(HankeEntity(hankeTunnus = TEST_HANKE_TUNNUS))
        val hankeId = hankeEntity.id!!
        val tulosEntity = prepareTulos(hankeEntity)
        entityManager.persist(tulosEntity)
        entityManager.flush()

        // Try to find something else than what was persisted
        val testResultEntityList = tormaystarkasteluTulosRepository.findByHankeId(hankeId + 1000)
        assertThat(testResultEntityList).isEmpty()
    }

    @Test
    fun `All fields are saved and loaded correctly`() {
        // Create parent Hanke (with mostly null data), and a tulos connected with it, persist hanke only:
        val hankeEntity = persistHanke(HankeEntity(hankeTunnus = TEST_HANKE_TUNNUS))
        val tulosEntity = prepareTulos(hankeEntity)

        // Save it (using TormaystarkasteluTulosRepository):
        tormaystarkasteluTulosRepository.save(tulosEntity)
        val tulosId = tulosEntity.id
        assertThat(tulosId).isNotNull()
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Load it back to different entity and check the fields
        val testResultEntity = tormaystarkasteluTulosRepository.findById(tulosId!!).get()
        assertThat(testResultEntity).isNotNull()
        assertThat(testResultEntity).isEqualTo(tulosEntity) // Checks almost all fields
    }

    @Test
    fun `Test that saving via TormaystarkasteluRepository works properly`() {
        // Create parent Hanke (with mostly null data), and a tulos connected with it, persist hanke only:
        val hankeEntity = persistHanke(HankeEntity(hankeTunnus = TEST_HANKE_TUNNUS))
        val hankeId = hankeEntity.id
        val tulosEntity = prepareTulos(hankeEntity)

        // Save tulos using TormaystarkasteluTulosRepository:
        tormaystarkasteluTulosRepository.save(tulosEntity)
        val tulosId = tulosEntity.id
        assertThat(tulosId).isNotNull()
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Load the parent hanke entity and check that the tulos can be found there
        val finalHankeEntity = hankeRepository.findById(hankeId!!).get()
        assertThat(finalHankeEntity.tormaystarkasteluTulokset).hasSize(1)
        // Load tulos directly with its repository:
        val finalTormaystarkasteluTulosEntityList = tormaystarkasteluTulosRepository.findByHankeId(hankeId)
        assertThat(finalTormaystarkasteluTulosEntityList).hasSize(1)
    }

    @Test
    fun `Test that saving via HankeRepository works properly`() {
        // Create parent Hanke (with mostly null data), and a tulos connected with it, persist hanke only:
        val hankeEntity = persistHanke(HankeEntity(hankeTunnus = TEST_HANKE_TUNNUS))
        val hankeId = hankeEntity.id
        val tulosEntity = prepareTulos(hankeEntity)
        assertThat(tulosEntity.id).isNull()

        // Save tulos using HankeRepository:
        hankeRepository.save(hankeEntity)
        // Note, tulos should have gotten saved indirectly via saving the parent Hanke,
        // and thus tulos should now have its own new id:
        assertThat(hankeEntity.tormaystarkasteluTulokset[0].id).isNotNull()
        entityManager.flush() // Make sure the stuff is run to database (though not necessarily committed)
        entityManager.clear() // Ensure the original entity is no longer in Hibernate's 1st level cache

        // Load the parent hanke entity and check that the tulos can be found there
        val finalHankeEntity = hankeRepository.findById(hankeId!!).get()
        assertThat(finalHankeEntity.tormaystarkasteluTulokset).hasSize(1)
        // Load tulos directly with its repository:
        val finalTormaystarkasteluTulosEntityList = tormaystarkasteluTulosRepository.findByHankeId(hankeId)
        assertThat(finalTormaystarkasteluTulosEntityList).hasSize(1)
    }

    @Test
    fun `Removing tulos from hanke using HankeRepository works`() {
        // Create parent Hanke (with mostly null data), and a tulos connected with it, persist both:
        val hankeEntity = persistHanke(HankeEntity(hankeTunnus = TEST_HANKE_TUNNUS))
        val hankeId = hankeEntity.id!!
        val tulosEntity = prepareTulos(hankeEntity)
        entityManager.persist(tulosEntity)
        entityManager.flush()
        val tulosId = tulosEntity.id

        // Check that it was saved
        val testResultEntity = tormaystarkasteluTulosRepository.findById(tulosId!!).get()
        assertThat(testResultEntity).isNotNull()
        assertThat(testResultEntity.hanke).isNotNull()
        assertThat(testResultEntity.hanke!!.tormaystarkasteluTulokset).hasSize(1)

        // Remove tulos from the hanke
        val testResultEntitysParent = testResultEntity.hanke
        testResultEntity.removeFromHanke()
        // If only saving/deleting the tulos, the hanke-side may in some cases revive the data.. so save parent hanke.
        hankeRepository.save(testResultEntitysParent!!)
        // This delete call is optional; the saving of hanke will also update the tulos-table
        //tormaystarkasteluTulosRepository.deleteById(testResultEntity.id!!)
        entityManager.flush()

        // Check that it got removed
        val finalHankeEntity = hankeRepository.findById(hankeId).get()
        assertThat(finalHankeEntity.tormaystarkasteluTulokset).isEmpty()
        val finalTormaystarkasteluTulosEntityList = tormaystarkasteluTulosRepository.findByHankeId(hankeId)
        assertThat(finalTormaystarkasteluTulosEntityList).isEmpty()
    }

//    @Test
//    fun `Removing hanke removes related tulos`() {
//        // TODO (if the removal of single tulos via saving hanke works, this should work, too, but
//        //   better to test it anyway.
//    }


    /**
     * Persists given HankeEntity to get a valid hanke Id and entity
     * (the relation between hanke and tormaystarkastelutulos things requires valid hanke id in the database)
     */
    private fun persistHanke(hankeEntity: HankeEntity): HankeEntity {
        val hankeEntity2 = entityManager.persist(hankeEntity)
        entityManager.flush()
        val hankeId = hankeEntity2.id
        assertThat(hankeId).isNotNull()
        return hankeEntity2
    }

    /**
     * Prepares a tulos entity and connects it with the given parent hanke, but does not save the new state.
     */
    private fun prepareTulos(hankeEntity: HankeEntity): TormaystarkasteluTulosEntity {
        val tulosEntity = TormaystarkasteluTulosEntity()
        tulosEntity.liikennehaitta = LiikennehaittaIndeksiType(TEST_INDEX_VALUE, IndeksiType.JOUKKOLIIKENNEINDEKSI)
        tulosEntity.perus = 1.1f
        tulosEntity.pyoraily = 2.2f
        tulosEntity.joukkoliikenne = TEST_INDEX_VALUE
        tulosEntity.tila = TormaystarkasteluTulosTila.VOIMASSA
        tulosEntity.tilaChangedAt = null
        tulosEntity.createdAt = DATETIME

        tulosEntity.addToHanke(hankeEntity)

        return tulosEntity
    }

}
