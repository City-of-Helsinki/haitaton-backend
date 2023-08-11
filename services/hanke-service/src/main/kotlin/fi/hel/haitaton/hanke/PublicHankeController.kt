package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.PublicHanke
import fi.hel.haitaton.hanke.domain.hankeToPublic
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public-hankkeet")
class PublicHankeController(private val hankeService: HankeService) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get list of public hanke",
        description =
            "A public hanke contains data that is visible to everyone. It does not contain private information."
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
                                        schema = Schema(implementation = PublicHanke::class)
                                    )
                            )
                        ]
                )
            ]
    )
    fun getAll(): List<PublicHanke> = hankeService.loadPublicHanke().map { hankeToPublic(it) }
}
