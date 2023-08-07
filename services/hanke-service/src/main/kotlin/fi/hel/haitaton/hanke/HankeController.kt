package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.ApplicationsResponse
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import fi.hel.haitaton.hanke.validation.ValidHanke
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
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
class HankeController(
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val disclosureLogService: DisclosureLogService,
    @Autowired private val featureFlags: FeatureFlags,
) {

    @GetMapping("/{hankeTunnus}", produces = [APPLICATION_JSON_VALUE])
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
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String): Hanke {
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        val userid = currentUserId()
        if (!permissionService.hasPermission(hanke.id!!, userid, PermissionCode.VIEW)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userid)
        return hanke
    }

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get hanke list",
        description = "Get list of hanke to which the user has permission to."
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

    @GetMapping("/{hankeTunnus}/hakemukset", produces = [APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get hanke applications",
        description = "Returns list of applications belonging to a given hanke."
    )
    fun getHankeHakemukset(@PathVariable hankeTunnus: String): ApplicationsResponse {
        logger.info { "Finding applications for hanke $hankeTunnus" }

        val userId = currentUserId()

        hankeService.getHankeWithApplications(hankeTunnus).let { (hanke, hakemukset) ->
            hanke.verifyUserAuthorization(userId, PermissionCode.VIEW)

            disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)
            if (hakemukset.isNotEmpty()) {
                disclosureLogService.saveDisclosureLogsForApplications(hakemukset, userId)
            }

            return ApplicationsResponse(hakemukset).also {
                logger.info { "Found ${it.applications.size} applications for hanke $hankeTunnus" }
            }
        }
    }

    @PostMapping(consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Create new hanke",
        description =
            """
              A valid new hanke must comply with the restrictions in Hanke schema definition.
              When Hanke is created:
              1. A unique Hanke tunnus is created.
              2. The status of the created hanke is set automatically:
                  - PUBLIC (i.e. visible to everyone) if all mandatory fields are filled. 
                  - DRAFT if all mandatory fields are not filled.
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
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke?): Hanke {
        featureFlags.ensureEnabled(Feature.HANKE_EDITING)

        if (hanke == null) {
            throw HankeArgumentException("No hanke given when creating hanke")
        }
        val sanitizedHanke = hanke.copy(id = null, generated = false)

        val userId = currentUserId()
        logger.info { "Creating Hanke for user $userId: ${hanke.toLogString()} " }

        val createdHanke = hankeService.createHanke(sanitizedHanke)

        permissionService.setPermission(
            createdHanke.id!!,
            currentUserId(),
            Role.KAIKKI_OIKEUDET,
        )
        disclosureLogService.saveDisclosureLogsForHanke(createdHanke, userId)
        return createdHanke
    }

    @PutMapping(
        "/{hankeTunnus}",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update hanke",
        description =
            "Update an existing hanke. An updated hanke must comply with the restrictions defined in HankeValidator."
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
    fun updateHanke(
        @ValidHanke @RequestBody hanke: Hanke,
        @PathVariable hankeTunnus: String
    ): Hanke {
        featureFlags.ensureEnabled(Feature.HANKE_EDITING)

        logger.info { "Updating Hanke: ${hanke.toLogString()}" }

        val existingHanke =
            hankeService.loadHanke(hankeTunnus)?.also {
                it.verifyUserAuthorization(currentUserId(), PermissionCode.EDIT)
            }
                ?: throw HankeNotFoundException(hankeTunnus)

        existingHanke.validateUpdatable(hanke, hankeTunnus)

        val updatedHanke = hankeService.updateHanke(hanke)
        logger.info { "Updated hanke ${updatedHanke.hankeTunnus}." }
        disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, updatedHanke.modifiedBy!!)
        return updatedHanke
    }

    @DeleteMapping("/{hankeTunnus}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete hanke", description = "Delete an existing hanke.")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success, no content", responseCode = "204"),
                ApiResponse(
                    description = "Hanke by requested hankeTunnus not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                )
            ]
    )
    fun deleteHanke(@PathVariable hankeTunnus: String) {
        logger.info { "Deleting hanke: $hankeTunnus" }

        hankeService.getHankeWithApplications(hankeTunnus).let { (hanke, hakemukset) ->
            val hankeId = hanke.id!!
            val userId = currentUserId()
            if (!permissionService.hasPermission(hankeId, userId, PermissionCode.DELETE)) {
                throw HankeNotFoundException(hankeTunnus)
            }
            hankeService.deleteHanke(hanke, hakemukset, userId)
            logger.info { "Deleted Hanke: ${hanke.toLogString()}" }
        }
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

    private fun Hanke.verifyUserAuthorization(userId: String, permissionCode: PermissionCode) {
        val hankeId = id
        if (hankeId == null || !permissionService.hasPermission(hankeId, userId, permissionCode)) {
            throw HankeNotFoundException(hankeTunnus)
        }
    }

    private fun Hanke.validateUpdatable(updatedHanke: Hanke, hankeTunnusFromPath: String) {
        if (hankeTunnusFromPath != updatedHanke.hankeTunnus) {
            throw HankeArgumentException("Hanketunnus not given or doesn't match the hanke data")
        }
        if (perustaja != null && perustaja != updatedHanke.perustaja) {
            throw HankeArgumentException("Updating perustaja not allowed.")
        }
    }
}
