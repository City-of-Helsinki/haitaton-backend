package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for HankeMapGridService focusing on cache repopulation scenarios. These tests
 * use real database and cache interactions.
 */
class HankeMapGridServiceITest(
    @Autowired private val hankeMapGridService: HankeMapGridService,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val cacheManager: CacheManager,
    @Autowired private val hankeRepository: HankeRepository,
) : IntegrationTest() {

    private lateinit var cache: Cache

    @BeforeEach
    fun setUp() {
        cache = cacheManager.getCache("publicHankeGrid")!!
        cache.clear()
    }

    @Nested
    inner class RepopulateCache {

        @Test
        @Transactional
        fun `populates empty cache when no public hanke exist`() {
            // Create only draft hanke - should not be cached
            hankeFactory.builder(USERNAME).withHankealue().saveEntity()

            hankeMapGridService.repopulateCache()

            // Verify some cells are cached as empty
            assertThat(getCacheValue("0,0")).isEmpty()
            assertThat(getCacheValue("10,29")).isEmpty()
            assertThat(getCacheValue("29,44")).isEmpty()
        }

        @Test
        @Transactional
        fun `populates cache with public hanke in correct cells`() {
            // Create public hanke with default geometry (should be in cell 10,29)
            val savedHanke =
                hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Verify hanke is cached in the expected cell (10,29) for default geometry
            val hankeInCell = getCacheValue("10,29")
            assertThat(hankeInCell).hasSize(1)
            assertThat((hankeInCell.first() as fi.hel.haitaton.hanke.domain.Hanke).id)
                .isEqualTo(savedHanke.id)

            // Verify other cells are cached as empty
            assertThat(getCacheValue("0,0")).isEmpty()
            assertThat(getCacheValue("29,44")).isEmpty()
        }

        @Test
        @Transactional
        fun `populates cache with multiple hanke in different cells`() {
            // Create hanke with default geometry (cell 10,29)
            val hanke1 =
                hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            // Create hanke with polygon geometry (cells 7,35 + 7,36)
            val hanke2 =
                hankeFactory
                    .builder(USERNAME)
                    .withHankealue(
                        alue =
                            HankealueFactory.create(
                                geometriat = GeometriaFactory.create(1, GeometriaFactory.polygon())
                            )
                    )
                    .saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Verify first hanke is in cell 10,29
            val hankeInDefaultCell = getCacheValue("10,29")
            assertThat(hankeInDefaultCell).hasSize(1)
            assertThat((hankeInDefaultCell.first() as fi.hel.haitaton.hanke.domain.Hanke).id)
                .isEqualTo(hanke1.id)

            // Verify second hanke is in polygon cells (7,35 and 7,36)
            val hankeInPolygonCell1 = getCacheValue("7,35")
            assertThat(hankeInPolygonCell1).hasSize(1)
            assertThat((hankeInPolygonCell1.first() as fi.hel.haitaton.hanke.domain.Hanke).id)
                .isEqualTo(hanke2.id)

            val hankeInPolygonCell2 = getCacheValue("7,36")
            assertThat(hankeInPolygonCell2).hasSize(1)
            assertThat((hankeInPolygonCell2.first() as fi.hel.haitaton.hanke.domain.Hanke).id)
                .isEqualTo(hanke2.id)

            // Verify empty cells are cached as empty
            assertThat(getCacheValue("0,0")).isEmpty()
        }

        @Test
        @Transactional
        fun `updates existing cache entries`() {
            // Initially populate with one hanke
            val hanke1 =
                hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Verify initial state
            assertThat(getCacheValue("10,29")).hasSize(1)
            assertThat(getCacheValue("0,0")).isEmpty()

            // Add another hanke to different cell
            hankeFactory
                .builder(USERNAME)
                .withHankealue(
                    alue =
                        HankealueFactory.create(
                            geometriat = GeometriaFactory.create(1, GeometriaFactory.polygon())
                        )
                )
                .saveEntity(HankeStatus.PUBLIC)

            // Repopulate cache
            hankeMapGridService.repopulateCache()

            // Verify both hanke are now cached correctly
            assertThat(getCacheValue("10,29")).hasSize(1)
            assertThat(getCacheValue("7,35")).hasSize(1)
            assertThat(getCacheValue("7,36")).hasSize(1)
            assertThat(getCacheValue("0,0")).isEmpty()
        }

        @Test
        @Transactional
        fun `removes hanke from cache when status changes to draft`() {
            // Create public hanke
            val savedHanke =
                hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Verify hanke is cached
            assertThat(getCacheValue("10,29")).hasSize(1)

            // Change hanke status to draft
            val hankeEntity = hankeRepository.getReferenceById(savedHanke.id)
            hankeEntity.status = HankeStatus.DRAFT
            hankeRepository.save(hankeEntity)

            // Repopulate cache
            hankeMapGridService.repopulateCache()

            // Verify hanke is no longer cached
            assertThat(getCacheValue("10,29")).isEmpty()
        }

        @Test
        @Transactional
        fun `caches hanke with overlapping geometries in multiple cells`() {
            // Create hanke with a large geometry that spans multiple cells
            val hanke =
                hankeFactory
                    .builder(USERNAME)
                    .withHankealue(
                        alue =
                            HankealueFactory.create(
                                geometriat = GeometriaFactory.create(1, GeometriaFactory.polygon())
                            )
                    )
                    .withHankealue(
                        alue =
                            HankealueFactory.create(
                                geometriat =
                                    GeometriaFactory.create(2, GeometriaFactory.thirdPolygon())
                            )
                    )
                    .saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Verify hanke appears in all relevant cells
            val cellsWithHanke = listOf("7,35", "7,36")
            cellsWithHanke.forEach { cellKey ->
                val hankeInCell = getCacheValue(cellKey)
                assertThat(hankeInCell).hasSize(1)
                assertThat((hankeInCell.first() as fi.hel.haitaton.hanke.domain.Hanke).id)
                    .isEqualTo(hanke.id)
            }
        }

        @Test
        @Transactional
        fun `clears cache before repopulation`() {
            // Manually add something to cache
            cache.put("test-key", listOf("test-value"))

            // Create public hanke
            hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Verify manual entry was cleared
            assertThat(cache.get("test-key")).isNull()

            // Verify real data was cached
            assertThat(getCacheValue("10,29")).hasSize(1)
        }

        private fun getCacheValue(cellKey: String) =
            cache.get(cellKey)?.get() as? List<*> ?: emptyList<Any>()
    }

    @Nested
    inner class LoadPublicHankeInGridCell {

        @Test
        @Transactional
        fun `returns cached result when cache is populated`() {
            // Create and cache hanke
            val savedHanke =
                hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            hankeMapGridService.repopulateCache()

            // Load from cache (cache condition prevents caching in test profile)
            val result = hankeMapGridService.loadPublicHankeInGridCell(10, 29)

            assertThat(result).hasSize(1)
            assertThat(result.first().id).isEqualTo(savedHanke.id)
            assertThat(result.first().hankeTunnus).isNotNull()
            assertThat(result.first().nimi).isNotNull()
        }

        @Test
        @Transactional
        fun `returns minimal hanke data structure`() {
            val savedHanke =
                hankeFactory
                    .builder(USERNAME)
                    .withYhteystiedot()
                    .withHankealue()
                    .saveEntity(HankeStatus.PUBLIC)

            val result = hankeMapGridService.loadPublicHankeInGridCell(10, 29)

            val hanke = result.first()
            // Verify minimal data is present
            assertThat(hanke.id).isEqualTo(savedHanke.id)
            assertThat(hanke.hankeTunnus).isEqualTo(savedHanke.hankeTunnus)
            assertThat(hanke.nimi).isEqualTo(savedHanke.nimi)
            assertThat(hanke.generated).isEqualTo(savedHanke.generated)
            assertThat(hanke.alueet).hasSize(1)

            // Verify minimal fields are null
            assertThat(hanke.kuvaus).isNull()
            assertThat(hanke.vaihe).isNull()
            assertThat(hanke.version).isNull()
            assertThat(hanke.status).isNull()
            assertThat(hanke.omistajat).isEmpty()
            assertThat(hanke.rakennuttajat).isEmpty()
            assertThat(hanke.toteuttajat).isEmpty()
            assertThat(hanke.muut).isEmpty()
        }

        @Test
        @Transactional
        fun `returns empty list for cell with no hanke`() {
            // Create hanke in default cell (10,29)
            hankeFactory.builder(USERNAME).withHankealue().saveEntity(HankeStatus.PUBLIC)

            // Query different cell that should be empty
            val result = hankeMapGridService.loadPublicHankeInGridCell(0, 0)

            assertThat(result).isEmpty()
        }
    }
}
