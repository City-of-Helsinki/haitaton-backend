package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(
    name = ["haitaton.features.user-management"],
    havingValue = "true",
)
class HakemusController(
    private val hakemusService: HakemusService,
    private val disclosureLogService: DisclosureLogService,
) {
    @GetMapping("/hakemukset/{id}")
    @Operation(
        summary = "Get one application",
        description = "Returns one application if it exists and the user can access it."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The requested application", responseCode = "200"),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'VIEW')")
    fun getById(@PathVariable(name = "id") id: Long): HakemusResponse {
        logger.info { "Finding application $id" }
        val response = hakemusService.hakemusResponse(id)
        disclosureLogService.saveDisclosureLogsForHakemusResponse(response, currentUserId())
        return response
    }

    @GetMapping("/hankkeet/{hankeTunnus}/hakemukset")
    @Operation(
        summary = "Get hanke applications",
        description = "Returns list of applications belonging to the given hanke."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Applications of the requested hanke",
                    responseCode = "200"
                ),
                ApiResponse(
                    description = "Hanke was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun getHankkeenHakemukset(@PathVariable hankeTunnus: String): HankkeenHakemuksetResponse {
        logger.info { "Finding applications for hanke $hankeTunnus" }
        val response = hakemusService.hankkeenHakemuksetResponse(hankeTunnus)
        logger.info { "Found ${response.applications.size} applications for hanke $hankeTunnus" }
        return response
    }

    @PutMapping("/hakemukset/{id}")
    @Operation(
        summary = "Update an application",
        description =
            """Returns the updated application.
               The application can be updated until it has been sent to Allu.
               If the application hasn't changed since the last update, nothing more is done.
               The pendingOnClient value can't be changed with this endpoint.
               Use [POST /hakemukset/{id}/send-application](#/application-controller/sendApplication) for that.
            """
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The updated application", responseCode = "200"),
                ApiResponse(
                    description = "Request contains invalid data",
                    responseCode = "400",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(oneOf = [HankeErrorDetail::class, HankeError::class]),
                                examples =
                                    [
                                        ExampleObject(
                                            name = "Validation error",
                                            summary = "Validation error example",
                                            value = "{hankeError: 'HAI2008', errorPaths: ['name']}"
                                        ),
                                        ExampleObject(
                                            name = "Incompatible request",
                                            summary = "Incompatible request example",
                                            value = "{hankeError: 'HAI2002'}"
                                        ),
                                    ]
                            )
                        ]
                ),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "The application can't be updated because it has been sent to Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'EDIT_APPLICATIONS')")
    fun update(
        @PathVariable(name = "id") id: Long,
        @ValidHakemusUpdateRequest @RequestBody request: HakemusUpdateRequest
    ): HakemusResponse {
        val userId = currentUserId()
        val response = hakemusService.updateHakemus(id, request, userId)
        disclosureLogService.saveDisclosureLogsForHakemusResponse(response, userId)
        return response
    }

    @ExceptionHandler(InvalidHakemusDataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusDataException(ex: InvalidHakemusDataException): HankeErrorDetail {
        logger.warn(ex) { ex.message }
        return HankeErrorDetail(hankeError = HankeError.HAI2008, errorPaths = ex.errorPaths)
    }

    @ExceptionHandler(ApplicationAlreadySentException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun applicationAlreadySentException(ex: ApplicationAlreadySentException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2009
    }

    @ExceptionHandler(IncompatibleHakemusUpdateRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun incompatibleHakemusUpdateRequestException(
        ex: IncompatibleHakemusUpdateRequestException
    ): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }

    @ExceptionHandler(ApplicationGeometryException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun applicationGeometryException(ex: ApplicationGeometryException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2005
    }

    @ExceptionHandler(InvalidHakemusyhteystietoException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusyhteystietoException(ex: InvalidHakemusyhteystietoException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2010
    }

    @ExceptionHandler(InvalidHakemusyhteyshenkiloException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusyhteyshenkiloException(ex: InvalidHakemusyhteyshenkiloException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2011
    }
}
