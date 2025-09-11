package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.PublicHanke
import fi.hel.haitaton.hanke.domain.PublicHankeMinimal
import fi.hel.haitaton.hanke.domain.toPublic
import fi.hel.haitaton.hanke.domain.toPublicMinimal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import java.time.LocalDate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class GridMetadata(
    val cellSizeMeters: Int,
    val originX: Double,
    val originY: Double,
    val maxX: Double,
    val maxY: Double,
)

@RestController
@RequestMapping("/public-hankkeet")
class PublicHankeController(
    private val hankeService: HankeService,
    private val hankeMapGridProperties: HankeMapGridProperties,
) {
    @GetMapping("/grid/metadata")
    @Operation(
        summary = "Get grid metadata for client-side caching",
        description =
            """
              Returns grid configuration parameters needed by the UI for client-side caching
              and proper grid cell calculations.
              
              This includes grid cell size in meters and the origin coordinates (0,0) 
              that define the grid system used by the /public-hankkeet/grid endpoint.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = GridMetadata::class))],
                )
            ]
    )
    fun getGridMetadata(): GridMetadata =
        GridMetadata(
            cellSizeMeters = hankeMapGridProperties.cellSizeMeters,
            originX = hankeMapGridProperties.originX,
            originY = hankeMapGridProperties.originY,
            maxX = hankeMapGridProperties.maxX,
            maxY = hankeMapGridProperties.maxY,
        )

    @PostMapping("/grid")
    @Operation(
        summary = "Get public hanke by map grid cells",
        description =
            """
              Returns minimal public hanke data optimized for map rendering.
              Contains only essential fields: id, hankeTunnus, nimi, and alueet 
              with geometry and tormaystarkastelu for map coloring.
              
              Uses map grid cells for locating the right hanke. Grid cell coordinates 
              must be non-negative and within the valid grid bounds. Use the 
              /grid/metadata endpoint to get the maximum valid coordinates.
              
              Returns HTTP 400 with error code HAI0003 if grid cell coordinates are invalid.
           """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content =
                        [
                            Content(
                                array =
                                    ArraySchema(
                                        schema = Schema(implementation = PublicHankeMinimal::class)
                                    )
                            )
                        ],
                )
            ]
    )
    fun getAllByDateAndGridCells(
        @RequestBody request: PublicHankeGridCellRequest
    ): List<PublicHankeMinimal> =
        hankeService
            .loadPublicHankeInGridCells(request.startDate, request.endDate, request.cells)
            .map { it.toPublicMinimal() }

    @GetMapping("/{hankeTunnus}")
    @Operation(
        summary = "Get full public hanke data by hankeTunnus",
        description =
            """
              Returns complete public hanke data for a single hanke by hankeTunnus.
              Contains all public fields including full area details with geometry, nuisance control plans,
              contact information, and project details.
              
              Only returns data if the hanke has PUBLIC status.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = PublicHanke::class))],
                ),
                ApiResponse(description = "Hanke not found or not public", responseCode = "404"),
            ]
    )
    fun get(@PathVariable hankeTunnus: String): PublicHanke =
        hankeService.loadPublicHankeByHankeTunnus(hankeTunnus).toPublic()
}

data class PublicHankeGridCellRequest(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val cells: List<GridCell>,
)
