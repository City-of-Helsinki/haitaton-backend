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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public-hankkeet")
class PublicHankeController(private val hankeService: HankeService) {

    @GetMapping
    @Operation(
        summary = "Get list of public hanke",
        description =
            """
              Returns minimal public hanke data optimized for map rendering.
              Contains only essential fields: id, hankeTunnus, nimi, and alueet 
              with geometry and tormaystarkastelu for map coloring.
              
              Supports optional bounding box filtering to load only hanke visible in the viewport:
              - minX, minY: bottom-left corner coordinates (ETRS-TM35FIN)
              - maxX, maxY: top-right corner coordinates (ETRS-TM35FIN)
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
    fun getAll(
        @RequestParam(required = false) minX: Double?,
        @RequestParam(required = false) minY: Double?,
        @RequestParam(required = false) maxX: Double?,
        @RequestParam(required = false) maxY: Double?,
    ): List<PublicHankeMinimal> {
        return if (minX != null && minY != null && maxX != null && maxY != null) {
            hankeService.loadPublicHankeWithinBounds(minX, minY, maxX, maxY).map {
                it.toPublicMinimal()
            }
        } else {
            hankeService.loadPublicHanke().map { it.toPublicMinimal() }
        }
    }

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
