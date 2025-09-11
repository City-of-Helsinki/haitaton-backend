package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

class HankeMapGridServiceTest {

    private val hankeRepository = mockk<HankeRepository>()
    private val hankeMapperService = mockk<HankeMapperService>()
    private val cacheManager = mockk<CacheManager>()
    private val properties =
        HankeMapGridProperties(
            cellSizeMeters = 1000,
            originX = 25486422.0,
            originY = 6643836.0,
            maxX = 25515423.0,
            maxY = 6687837.0,
        )
    private val service =
        HankeMapGridService(hankeRepository, hankeMapperService, properties, cacheManager)

    private val cache = mockk<Cache>()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { cacheManager.getCache("publicHankeGrid") } returns cache
    }

    @Nested
    inner class GetCellBounds {

        @Test
        fun `throws exception for negative cell coordinates`() {
            assertThrows<InvalidGridCellException> { service.getCellBounds(-1, 0) }

            assertThrows<InvalidGridCellException> { service.getCellBounds(0, -1) }
        }

        @Test
        fun `throws exception for out-of-bounds coordinates`() {
            // Max coordinates are ((25515423.0 - 25486422.0) / 1000) = 29 for X
            // and ((6687837.0 - 6643836.0) / 1000) = 44 for Y
            assertThrows<InvalidGridCellException> { service.getCellBounds(30, 0) }

            assertThrows<InvalidGridCellException> { service.getCellBounds(0, 45) }
        }

        @Test
        fun `succeeds for valid coordinates`() {
            service.getCellBounds(0, 0)
            service.getCellBounds(29, 44) // Max valid coordinates
            service.getCellBounds(15, 22) // Mid-range coordinates
        }

        @Test
        fun `calculates correct bounds for origin cell`() {
            val result = service.getCellBounds(0, 0)

            assertThat(result.minX).isEqualTo(25486422.0)
            assertThat(result.minY).isEqualTo(6643836.0)
            assertThat(result.maxX).isEqualTo(25487422.0)
            assertThat(result.maxY).isEqualTo(6644836.0)
        }

        @Test
        fun `calculates correct bounds for cell (10, 29)`() {
            val result = service.getCellBounds(10, 29)

            assertThat(result.minX).isEqualTo(25496422.0)
            assertThat(result.minY).isEqualTo(6672836.0)
            assertThat(result.maxX).isEqualTo(25497422.0)
            assertThat(result.maxY).isEqualTo(6673836.0)
        }
    }

    @Nested
    inner class LoadPublicHankeInGridCell {

        @Test
        fun `returns empty list when no hanke found in cell`() {
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns emptyList()

            val result = service.loadPublicHankeInGridCell(10, 29)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns mapped hanke when found in cell`() {
            val hankeEntity = HankeFactory.createEntity()
            val mappedHanke = mockk<Hanke>()

            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    25496422.0,
                    6672836.0,
                    25497422.0,
                    6673836.0,
                )
            } returns listOf(hankeEntity)
            every { hankeMapperService.minimalDomainFrom(hankeEntity) } returns mappedHanke

            val result = service.loadPublicHankeInGridCell(10, 29)

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(mappedHanke)
        }

        @Test
        fun `returns multiple mapped hanke when multiple found in cell`() {
            val hankeEntity1 = HankeFactory.createEntity(mockId = 1)
            val hankeEntity2 = HankeFactory.createEntity(mockId = 2)
            val mappedHanke1 = mockk<Hanke>()
            val mappedHanke2 = mockk<Hanke>()

            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    25496422.0,
                    6672836.0,
                    25497422.0,
                    6673836.0,
                )
            } returns listOf(hankeEntity1, hankeEntity2)
            every { hankeMapperService.minimalDomainFrom(hankeEntity1) } returns mappedHanke1
            every { hankeMapperService.minimalDomainFrom(hankeEntity2) } returns mappedHanke2

            val result = service.loadPublicHankeInGridCell(10, 29)

            assertThat(result).hasSize(2)
            assertThat(result).isEqualTo(listOf(mappedHanke1, mappedHanke2))
        }

        @Test
        fun `uses correct status parameter`() {
            val statusSlot = slot<String>()
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    capture(statusSlot),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns emptyList()

            service.loadPublicHankeInGridCell(0, 0)

            assertThat(statusSlot.captured).isEqualTo("PUBLIC")
        }

        @Test
        fun `validates cell coordinates before querying`() {
            assertThrows<InvalidGridCellException> { service.loadPublicHankeInGridCell(-1, 0) }

            // Verify repository was not called
            verify(exactly = 0) {
                hankeRepository.findAllByStatusWithinBounds(any(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    inner class RepopulateCache {

        @Test
        fun `handles null cache gracefully during repopulation`() {
            every { cacheManager.getCache("publicHankeGrid") } returns null
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns emptyList()

            // Should not throw exception even with null cache
            service.repopulateCache()

            verify { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) }
        }

        @Test
        fun `continues repopulation when cache put operations fail`() {
            every { cacheManager.getCache("publicHankeGrid") } returns cache
            every { cache.clear() } returns Unit
            every { cache.put(any(), any()) } throws RuntimeException("Cache put failed")
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns emptyList()
            every {
                hankeRepository.findAllByStatusWithinBounds(any(), any(), any(), any(), any())
            } returns emptyList()

            // Should not propagate cache put exceptions (they're caught in the grid cell loop)
            service.repopulateCache()

            verify { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) }
            verify { cache.clear() }
        }

        @Test
        fun `propagates cache clear failures`() {
            every { cacheManager.getCache("publicHankeGrid") } returns cache
            every { cache.clear() } throws RuntimeException("Cache clear failed")
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns emptyList()

            // Should propagate cache clear exceptions
            assertThrows<RuntimeException> { service.repopulateCache() }

            verify { cache.clear() }
            verify { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) }
        }

        @Test
        fun `handles repository exceptions during repopulation`() {
            every { cacheManager.getCache("publicHankeGrid") } returns cache
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } throws
                RuntimeException("Database connection failed")

            // Should propagate repository exceptions
            assertThrows<RuntimeException> { service.repopulateCache() }
        }

        @Test
        fun `handles spatial query exceptions for individual cells`() {
            val hankeEntity = HankeFactory.createEntity()
            val mappedHanke = mockk<fi.hel.haitaton.hanke.domain.Hanke>()

            every { cacheManager.getCache("publicHankeGrid") } returns cache
            every { cache.clear() } returns Unit
            every { cache.put(any(), any()) } returns Unit
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns
                listOf(hankeEntity)
            every { hankeMapperService.minimalDomainFrom(hankeEntity) } returns mappedHanke

            // First few cells throw exceptions
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    any(),
                    25486422.0,
                    6643836.0,
                    25487422.0,
                    6644836.0, // Cell (0,0)
                )
            } throws RuntimeException("Spatial query failed for cell (0,0)")

            every {
                hankeRepository.findAllByStatusWithinBounds(
                    any(),
                    25487422.0,
                    6643836.0,
                    25488422.0,
                    6644836.0, // Cell (1,0)
                )
            } throws RuntimeException("Spatial query failed for cell (1,0)")

            // All other cells succeed
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    any(),
                    not(eq(25486422.0)),
                    any(),
                    any(),
                    any(),
                )
            } returns emptyList()
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    any(),
                    any(),
                    not(eq(6643836.0)),
                    any(),
                    any(),
                )
            } returns emptyList()

            // Should complete despite individual cell failures
            service.repopulateCache()

            // Should have attempted to cache successful cells
            verify(atLeast = 1300) { cache.put(any(), any()) }
        }

        @Test
        fun `clears existing cache before repopulation`() {
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns emptyList()
            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            verify { cache.clear() }
        }

        @Test
        fun `handles empty hanke list`() {
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns emptyList()
            every {
                hankeRepository.findAllByStatusWithinBounds(any(), any(), any(), any(), any())
            } returns emptyList()
            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            verify { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) }
            verify { cache.clear() }
            // Should cache empty results for all cells (30 * 45 = 1350 cells)
            verify(exactly = 1350) { cache.put(any(), emptyList<Hanke>()) }
        }

        @Test
        fun `populates cache for all grid cells`() {
            val hankeEntity = HankeFactory.createEntity(mockId = 1)
            val mappedHanke = mockk<Hanke> { every { id } returns 1 }

            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns
                listOf(hankeEntity)
            every { hankeMapperService.minimalDomainFrom(hankeEntity) } returns mappedHanke
            every {
                hankeRepository.findAllByStatusWithinBounds(any(), any(), any(), any(), any())
            } returns emptyList()
            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            // Should process all grid cells (30 columns * 45 rows = 1350 cells)
            verify(exactly = 1350) { cache.put(any(), any()) }
            verify { cache.clear() }
        }

        @Test
        fun `caches hanke in correct grid cells`() {
            val hankeEntity = HankeFactory.createEntity(mockId = 1)
            val mappedHanke = mockk<Hanke> { every { id } returns 1 }

            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns
                listOf(hankeEntity)
            every { hankeMapperService.minimalDomainFrom(hankeEntity) } returns mappedHanke

            // Mock the spatial query to return the hanke for a specific cell
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    25496422.0,
                    6672836.0,
                    25497422.0,
                    6673836.0, // Cell (10, 29)
                )
            } returns listOf(hankeEntity)

            // All other cells return empty
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    not(eq(25496422.0)),
                    any(),
                    any(),
                    any(),
                )
            } returns emptyList()
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    25496422.0,
                    not(eq(6672836.0)),
                    any(),
                    any(),
                )
            } returns emptyList()

            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            // Verify hanke is cached in the correct cell
            verify { cache.put("10,29", listOf(mappedHanke)) }

            // Verify other cells are cached as empty (spot check a few)
            verify { cache.put("0,0", emptyList<Hanke>()) }
            verify { cache.put("29,44", emptyList<Hanke>()) }
        }

        @Test
        fun `handles repository exceptions gracefully`() {
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } throws
                RuntimeException("Database error")

            assertThrows<RuntimeException> { service.repopulateCache() }

            verify { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) }
            verify { cache wasNot Called }
        }

        @Test
        fun `handles spatial query exceptions and continues with other cells`() {
            val hankeEntity = HankeFactory.createEntity(mockId = 1)
            val mappedHanke = mockk<Hanke> { every { id } returns 1 }

            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns
                listOf(hankeEntity)
            every { hankeMapperService.minimalDomainFrom(hankeEntity) } returns mappedHanke

            // First cell throws exception
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    25486422.0,
                    6643836.0,
                    25487422.0,
                    6644836.0, // Cell (0, 0)
                )
            } throws RuntimeException("Spatial query failed")

            // Other cells succeed
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    not(eq(25486422.0)),
                    any(),
                    any(),
                    any(),
                )
            } returns emptyList()
            every {
                hankeRepository.findAllByStatusWithinBounds(
                    HankeStatus.PUBLIC.name,
                    25486422.0,
                    not(eq(6643836.0)),
                    any(),
                    any(),
                )
            } returns emptyList()

            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            // Should still cache successful cells, but not the failing one
            verify(atLeast = 1349) { cache.put(any(), any()) }
            verify { cache.clear() }
        }

        @Test
        fun `uses correct cache key format`() {
            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns emptyList()
            every {
                hankeRepository.findAllByStatusWithinBounds(any(), any(), any(), any(), any())
            } returns emptyList()
            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            // Verify cache key format (cellX,cellY)
            verify { cache.put("0,0", any()) }
            verify { cache.put("10,29", any()) }
            verify { cache.put("29,44", any()) }
        }

        @Test
        fun `maps hanke entities to domain objects only once`() {
            val hankeEntity1 = HankeFactory.createEntity(mockId = 1)
            val hankeEntity2 = HankeFactory.createEntity(mockId = 2)
            val mappedHanke1 = mockk<Hanke> { every { id } returns 1 }
            val mappedHanke2 = mockk<Hanke> { every { id } returns 2 }

            every { hankeRepository.findAllByStatus(HankeStatus.PUBLIC) } returns
                listOf(hankeEntity1, hankeEntity2)
            every { hankeMapperService.minimalDomainFrom(hankeEntity1) } returns mappedHanke1
            every { hankeMapperService.minimalDomainFrom(hankeEntity2) } returns mappedHanke2
            every {
                hankeRepository.findAllByStatusWithinBounds(any(), any(), any(), any(), any())
            } returns emptyList()
            justRun { cache.clear() }
            justRun { cache.put(any(), any()) }

            service.repopulateCache()

            // Verify each hanke entity is mapped exactly once
            verify(exactly = 1) { hankeMapperService.minimalDomainFrom(hankeEntity1) }
            verify(exactly = 1) { hankeMapperService.minimalDomainFrom(hankeEntity2) }
        }
    }
}
