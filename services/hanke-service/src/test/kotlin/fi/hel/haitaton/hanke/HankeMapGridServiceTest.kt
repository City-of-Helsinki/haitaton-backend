package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertions.hasClass
import assertk.assertions.messageContains
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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

    @Nested
    inner class GetCellBounds {

        @Test
        fun `throws exception for negative cell coordinates`() {
            assertFailure { service.getCellBounds(-1, 0) }
                .all {
                    hasClass(InvalidGridCellException::class)
                    messageContains("Grid cell coordinates must be non-negative")
                    messageContains("(-1, 0)")
                }

            assertFailure { service.getCellBounds(0, -1) }
                .all {
                    hasClass(InvalidGridCellException::class)
                    messageContains("Grid cell coordinates must be non-negative")
                    messageContains("(0, -1)")
                }
        }

        @Test
        fun `throws exception for out-of-bounds coordinates`() {
            // Max coordinates are ((25515423.0 - 25486422.0) / 1000) = 29 for X
            // and ((6687837.0 - 6643836.0) / 1000) = 44 for Y
            assertFailure { service.getCellBounds(30, 0) }
                .all {
                    hasClass(InvalidGridCellException::class)
                    messageContains("outside the valid range")
                    messageContains("(30, 0)")
                    messageContains("Maximum coordinates are (29, 44)")
                }

            assertFailure { service.getCellBounds(0, 45) }
                .all {
                    hasClass(InvalidGridCellException::class)
                    messageContains("outside the valid range")
                    messageContains("(0, 45)")
                    messageContains("Maximum coordinates are (29, 44)")
                }
        }

        @Test
        fun `succeeds for valid coordinates`() {
            service.getCellBounds(0, 0)
            service.getCellBounds(29, 44) // Max valid coordinates
            service.getCellBounds(15, 22) // Mid-range coordinates
        }
    }
}
