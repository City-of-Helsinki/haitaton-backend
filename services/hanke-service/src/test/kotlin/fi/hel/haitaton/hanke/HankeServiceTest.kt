package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.domain.Hanke
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HankeServiceTest {

    private val hankeMapGridService = mockk<HankeMapGridService>()
    private val service =
        HankeService(
            hankeRepository = mockk(),
            hanketunnusService = mockk(),
            hankealueService = mockk(),
            hankeLoggingService = mockk(),
            hankeMapGridService = hankeMapGridService,
            hankeMapperService = mockk(),
            hakemusService = mockk(),
            hankeKayttajaService = mockk(),
            hankeAttachmentService = mockk(),
            geometriatDao = mockk(),
        )

    companion object {
        private const val HANKETUNNUS_1 = "HAI24-1"
        private const val HANKETUNNUS_2 = "HAI24-2"
    }

    @Nested
    inner class LoadPublicHankeInGridCells {

        private val startDate = LocalDate.of(2024, 1, 1)
        private val endDate = LocalDate.of(2024, 12, 31)
        private val cells = listOf(GridCell(10, 29), GridCell(11, 29))

        @Test
        fun `handles grid service exceptions gracefully`() {
            val startDate = LocalDate.of(2024, 1, 1)
            val endDate = LocalDate.of(2024, 12, 31)
            val cells = listOf(GridCell(10, 29), GridCell(11, 29))

            // First cell succeeds, second fails
            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns emptyList()
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } throws
                RuntimeException("Grid service failed")

            // Should propagate the exception
            assertThrows<RuntimeException> {
                service.loadPublicHankeInGridCells(startDate, endDate, cells)
            }
        }

        @Test
        fun `handles invalid grid cell exceptions from grid service`() {
            val startDate = LocalDate.of(2024, 1, 1)
            val endDate = LocalDate.of(2024, 12, 31)
            val invalidCells = listOf(GridCell(-1, -1))

            every { hankeMapGridService.loadPublicHankeInGridCell(-1, -1) } throws
                InvalidGridCellException("Invalid coordinates")

            // Should propagate the validation exception
            assertThrows<InvalidGridCellException> {
                service.loadPublicHankeInGridCells(startDate, endDate, invalidCells)
            }
        }

        @Test
        fun `handles empty date range edge cases`() {
            val hanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        LocalDate.of(2024, 6, 1).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        LocalDate.of(2024, 6, 30).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns listOf(hanke)

            // Query with inverted date range (end before start)
            val startDate = LocalDate.of(2024, 12, 31)
            val endDate = LocalDate.of(2024, 1, 1)
            val cells = listOf(GridCell(10, 29))

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            // Should return empty list for invalid date range
            assertThat(result).isEmpty()
        }

        @Test
        fun `handles large number of grid cells`() {
            // Create a large number of cells including some invalid ones
            val validCells = (0..29).flatMap { x -> (0..44).map { y -> GridCell(x, y) } }.take(100)

            val invalidCells = listOf(GridCell(30, 0), GridCell(0, 45), GridCell(100, 200))

            val cells = validCells + invalidCells

            // Mock responses for valid cells
            validCells.forEach { cell ->
                every { hankeMapGridService.loadPublicHankeInGridCell(cell.x, cell.y) } returns
                    emptyList()
            }

            // Mock exceptions for invalid cells
            invalidCells.forEach { cell ->
                every { hankeMapGridService.loadPublicHankeInGridCell(cell.x, cell.y) } throws
                    InvalidGridCellException("Invalid cell: ${cell.x}, ${cell.y}")
            }

            val startDate = LocalDate.of(2024, 1, 1)
            val endDate = LocalDate.of(2024, 12, 31)

            // Should fail on first invalid cell
            assertThrows<InvalidGridCellException> {
                service.loadPublicHankeInGridCells(startDate, endDate, cells)
            }
        }

        @Test
        fun `returns empty list when no cells provided`() {
            val result = service.loadPublicHankeInGridCells(startDate, endDate, emptyList())

            assertThat(result).isEmpty()
            verify(exactly = 0) { hankeMapGridService.loadPublicHankeInGridCell(any(), any()) }
        }

        @Test
        fun `returns empty list when no hanke found in any cell`() {
            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns emptyList()
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).isEmpty()
            verify { hankeMapGridService.loadPublicHankeInGridCell(10, 29) }
            verify { hankeMapGridService.loadPublicHankeInGridCell(11, 29) }
        }

        @Test
        fun `returns hanke from single cell when dates overlap`() {
            val hanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_1
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns listOf(hanke)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(hanke)
        }

        @Test
        fun `filters out hanke when dates do not overlap`() {
            val hankeOutsideRange =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        LocalDate.of(2023, 1, 1).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        LocalDate.of(2023, 12, 31).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns
                listOf(hankeOutsideRange)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).isEmpty()
        }

        @Test
        fun `includes hanke when alkuPvm overlaps with end of range`() {
            val hankeOverlapping =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.plusDays(10).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_2
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns
                listOf(hankeOverlapping)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(hankeOverlapping)
        }

        @Test
        fun `includes hanke when loppuPvm overlaps with start of range`() {
            val hankeOverlapping =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.minusDays(10).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns "HAI24-3"
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns
                listOf(hankeOverlapping)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(hankeOverlapping)
        }

        @Test
        fun `excludes hanke when alkuPvm is null`() {
            val hankeWithoutStart =
                mockk<Hanke> {
                    every { alkuPvm } returns null
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns
                listOf(hankeWithoutStart)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).isEmpty()
        }

        @Test
        fun `excludes hanke when loppuPvm is null`() {
            val hankeWithoutEnd =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns null
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns
                listOf(hankeWithoutEnd)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns hanke from multiple cells`() {
            val hanke1 =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_1
                }
            val hanke2 =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_2
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns listOf(hanke1)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns listOf(hanke2)

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).hasSize(2)
        }

        @Test
        fun `removes duplicates when same hanke appears in multiple cells`() {
            val hanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_1
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns listOf(hanke)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns listOf(hanke)

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(hanke)
        }

        @Test
        fun `loads all requested cells even when some are empty`() {
            val hanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns "HAI24-4"
                }

            val threeCells = listOf(GridCell(10, 29), GridCell(11, 29), GridCell(12, 29))
            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns listOf(hanke)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()
            every { hankeMapGridService.loadPublicHankeInGridCell(12, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, threeCells)

            // Verify all cells were queried
            verify { hankeMapGridService.loadPublicHankeInGridCell(10, 29) }
            verify { hankeMapGridService.loadPublicHankeInGridCell(11, 29) }
            verify { hankeMapGridService.loadPublicHankeInGridCell(12, 29) }

            assertThat(result).hasSize(1)
        }

        @Test
        fun `handles single cell input`() {
            val hanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns "HAI24-4"
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns listOf(hanke)

            val result =
                service.loadPublicHankeInGridCells(startDate, endDate, listOf(GridCell(10, 29)))

            assertThat(result).hasSize(1)
            assertThat(result.first()).isEqualTo(hanke)
        }

        @Test
        fun `applies date filtering after loading from all cells`() {
            val validHanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        startDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        endDate.atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_1
                }
            val invalidHanke =
                mockk<Hanke> {
                    every { alkuPvm } returns
                        LocalDate.of(2025, 1, 1).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { loppuPvm } returns
                        LocalDate.of(2025, 12, 31).atStartOfDay().atZone(java.time.ZoneOffset.UTC)
                    every { hankeTunnus } returns HANKETUNNUS_2
                }

            every { hankeMapGridService.loadPublicHankeInGridCell(10, 29) } returns
                listOf(validHanke, invalidHanke)
            every { hankeMapGridService.loadPublicHankeInGridCell(11, 29) } returns emptyList()

            val result = service.loadPublicHankeInGridCells(startDate, endDate, cells)

            assertThat(result).hasSize(1)
            assertThat(result.first().hankeTunnus).isEqualTo(HANKETUNNUS_1)
        }
    }
}
