package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import mu.KotlinLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class HankeMapGridService(
    private val hankeRepository: HankeRepository,
    private val hankeMapperService: HankeMapperService,
    private val properties: HankeMapGridProperties,
    private val cacheManager: CacheManager,
) {
    /**
     * Loads all public hanke in the given grid cell. The results are cached based on cell
     * coordinates.
     */
    @Cacheable(
        value = ["publicHankeGrid"],
        key = "#cellX + ',' + #cellY",
        condition = "!@environment.acceptsProfiles('test')",
    )
    fun loadPublicHankeInGridCell(cellX: Int, cellY: Int): List<Hanke> {
        val bounds = getCellBounds(cellX, cellY)

        return hankeRepository
            .findAllByStatusWithinBounds(
                HankeStatus.PUBLIC.name,
                bounds.minX,
                bounds.minY,
                bounds.maxX,
                bounds.maxY,
            )
            .map { hankeMapperService.minimalDomainFrom(it) }
    }

    /**
     * Repopulates the entire cache by loading all public hanke and caching both populated and empty
     * cells to prevent unnecessary database queries.
     */
    @Transactional(readOnly = true)
    fun repopulateCache() {
        logger.info { "Starting cache repopulation for public hanke grid" }

        // Load all public hanke with their geometries in one query
        val publicHankeEntities = hankeRepository.findAllByStatus(HankeStatus.PUBLIC)
        logger.info { "Loaded ${publicHankeEntities.size} public hanke from database" }

        // Convert entities to domain objects once
        val publicHanke = publicHankeEntities.map { hankeMapperService.minimalDomainFrom(it) }

        // Clear existing cache
        val cache = cacheManager.getCache("publicHankeGrid")
        cache?.clear()

        // Calculate grid bounds
        val minCellX = 0
        val maxCellX = ((properties.maxX - properties.originX) / properties.cellSizeMeters).toInt()
        val minCellY = 0
        val maxCellY = ((properties.maxY - properties.originY) / properties.cellSizeMeters).toInt()
        val totalCells = (maxCellX + 1) * (maxCellY + 1)

        var populatedCells = 0
        var emptyCells = 0
        var failedCells = 0

        // Cache ALL cells in the grid area
        for (cellX in minCellX..maxCellX) {
            for (cellY in minCellY..maxCellY) {
                try {
                    val cellBounds = getCellBounds(cellX, cellY)

                    // Find hanke that intersect with this cell using spatial query
                    val hankeInCell =
                        hankeRepository
                            .findAllByStatusWithinBounds(
                                HankeStatus.PUBLIC.name,
                                cellBounds.minX,
                                cellBounds.minY,
                                cellBounds.maxX,
                                cellBounds.maxY,
                            )
                            .mapNotNull { entity ->
                                // Find corresponding domain object
                                publicHanke.find { it.id == entity.id }
                            }

                    // Cache the result (either list of hanke or empty list)
                    cache?.put("$cellX,$cellY", hankeInCell)

                    if (hankeInCell.isEmpty()) {
                        emptyCells++
                    } else {
                        populatedCells++
                    }
                } catch (e: Exception) {
                    failedCells++
                    // Individual cell failures are tracked in the summary count
                }
            }
        }

        logger.info {
            "Cache repopulation completed. Total cells: $totalCells " +
                "(populated: $populatedCells, empty: $emptyCells, " +
                "failed: $failedCells, total hanke objects: ${publicHanke.size})"
        }
    }

    internal fun getCellBounds(cellX: Int, cellY: Int): CellBounds {
        validateCellCoordinates(cellX, cellY)

        val minX = properties.originX + (cellX * properties.cellSizeMeters)
        val minY = properties.originY + (cellY * properties.cellSizeMeters)
        val maxX = minX + properties.cellSizeMeters
        val maxY = minY + properties.cellSizeMeters

        return CellBounds(minX, minY, maxX, maxY)
    }

    private fun validateCellCoordinates(cellX: Int, cellY: Int) {
        val maxCellX = ((properties.maxX - properties.originX) / properties.cellSizeMeters).toInt()
        val maxCellY = ((properties.maxY - properties.originY) / properties.cellSizeMeters).toInt()

        if (cellX < 0 || cellY < 0) {
            throw InvalidGridCellException(
                "Grid cell coordinates must be non-negative. Got: ($cellX, $cellY)"
            )
        }

        if (cellX > maxCellX || cellY > maxCellY) {
            throw InvalidGridCellException(
                "Grid cell coordinates ($cellX, $cellY) are outside the valid range. " +
                    "Maximum coordinates are ($maxCellX, $maxCellY)"
            )
        }
    }
}

data class CellBounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

data class GridCell(val x: Int, val y: Int) {
    override fun toString(): String = "($x, $y)"
}

@ConfigurationProperties(prefix = "haitaton.hanke.map-grid")
data class HankeMapGridProperties(
    var cellSizeMeters: Int = 1000,
    var originX: Double = 25486422.0,
    var originY: Double = 6643836.0,
    var maxX: Double = 25515423.0,
    var maxY: Double = 6687837.0,
)
