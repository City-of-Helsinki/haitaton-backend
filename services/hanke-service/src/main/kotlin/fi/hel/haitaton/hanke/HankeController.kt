package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.ModifyHankeRequest
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.validation.ValidCreateHankeRequest
import fi.hel.haitaton.hanke.validation.ValidHanke
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hankkeet")
@Validated
@SecurityRequirement(name = "bearerAuth")
class HankeController(
    private val hankeService: HankeService,
    private val permissionService: PermissionService,
    private val disclosureLogService: DisclosureLogService,
) {

    @GetMapping("/{hankeTunnus}")
    @Operation(summary = "Get hanke", description = "Get specific hanke by hankeTunnus.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = Hanke::class))]
                ),
                ApiResponse(
                    description = "Hanke by requested hankeTunnus not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                )
            ]
    )
    @PreAuthorize("@hankeAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String): Hanke {
        val hanke = hankeService.loadHanke(hankeTunnus)!!
        disclosureLogService.saveDisclosureLogsForHanke(hanke, currentUserId())
        return hanke
    }

    @GetMapping
    @Operation(
        summary = "Get hanke list",
        description =
            """Get Hanke list to which the user has permission to. 
            Contains e.g. personal contact information, which is not available in a public Hanke."""
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
                                array = ArraySchema(schema = Schema(implementation = Hanke::class))
                            )
                        ]
                )
            ]
    )
    fun getHankeList(
        @Parameter(
            description =
                """Boolean flag indicating whether endpoint should return geometry data for alueet. 
                    Geometriat fields will be null if false.""",
            schema = Schema(type = "boolean", defaultValue = "false"),
            required = false,
            example = "false",
        )
        @RequestParam
        geometry: Boolean = false
    ): List<Hanke> {
        val userid = currentUserId()

        val hankeIds = permissionService.getAllowedHankeIds(userid, PermissionCode.VIEW)
        val hankeList = hankeService.loadHankkeetByIds(hankeIds)

        if (!geometry) {
            hankeList.forEach { hanke -> hanke.alueet.forEach { alue -> alue.geometriat = null } }
        }

        disclosureLogService.saveDisclosureLogsForHankkeet(hankeList, userid)
        return hankeList
    }

    @PostMapping
    @Operation(
        summary = "Create new hanke",
        description =
            """
A hanke is created with minimum information, but all information is necessary.
The hanke can be updated with the PUT endpoint after creation to flesh it out.

When Hanke is created:
1. A unique Hanke tunnus is created.
2. The status of the hanke is set as DRAFT.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = Hanke::class))]
                ),
                ApiResponse(
                    description = "The request body was invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                )
            ]
    )
    @PreAuthorize("@featureService.isEnabled('HANKE_EDITING')")
    fun createHanke(
        @ValidCreateHankeRequest @RequestBody request: CreateHankeRequest,
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext
    ): Hanke {
        logger.info { "Creating Hanke..." }

        val createdHanke = hankeService.createHanke(request, securityContext)

        disclosureLogService.saveDisclosureLogsForHanke(createdHanke, currentUserId())
        logger.info { "Created Hanke ${createdHanke.hankeTunnus}." }
        return createdHanke
    }

    @PutMapping("/{hankeTunnus}")
    @Operation(
        summary = "Update hanke",
        description =
            """
Update an existing hanke. Data must comply with the restrictions defined in Hanke schema definition.

On update following will happen automatically:
1. Status is updated. PUBLIC if required fields are filled. Else DRAFT.
2. Tormaystarkastelu (project nuisance) is re-calculated.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = Hanke::class))]
                ),
                ApiResponse(
                    description = "The request body was invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Hanke by requested tunnus not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                )
            ]
    )
    @PreAuthorize(
        "@featureService.isEnabled('HANKE_EDITING') && " +
            "@hankeAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'EDIT')"
    )
    fun updateHanke(
        @ValidHanke @RequestBody hankeUpdate: ModifyHankeRequest,
        @PathVariable hankeTunnus: String
    ): Hanke {
        logger.info { "Updating Hanke: $hankeTunnus" }

        val updatedHanke = hankeService.updateHanke(hankeTunnus, hankeUpdate)
        logger.info { "Updated hanke ${updatedHanke.hankeTunnus}." }
        disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, updatedHanke.modifiedBy!!)
        return updatedHanke
    }

    @DeleteMapping("/{hankeTunnus}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete hanke",
        description =
            "Delete an existing hanke. Deletion is not possible if Hanke contains active applications."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success, no content", responseCode = "204"),
                ApiResponse(
                    description = "Hanke by requested hankeTunnus not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Hanke has active application(s) in Allu, will not delete.",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                )
            ]
    )
    @PreAuthorize("@hankeAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'DELETE')")
    fun deleteHanke(@PathVariable hankeTunnus: String) {
        logger.info { "Deleting hanke: $hankeTunnus" }

        hankeService.deleteHanke(hankeTunnus, currentUserId())
        logger.info { "Deleted Hanke: $hankeTunnus" }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HankeArgumentException::class)
    @Hidden
    fun handleArgumentExceptions(ex: HankeArgumentException): HankeError {
        logger.warn { ex.message }
        return HankeError.HAI1002
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    @Hidden
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1002)
    }
}
