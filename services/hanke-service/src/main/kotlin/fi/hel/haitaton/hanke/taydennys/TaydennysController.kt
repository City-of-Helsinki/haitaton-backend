package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.ValidHakemusUpdateRequest
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
class TaydennysController(private val taydennysService: TaydennysService) {

    @PostMapping("/hakemukset/{id}/taydennys")
    @Operation(
        summary = "Create a new response to an information request, i.e. a täydennys.",
        description =
            """
                Returns the created täydennys. The täydennys starts with a copy of the
                data the application has.

                The application needs to be in WAITING_INFORMATION status and there needs
                to be an open information request for the application. 
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The created täydennys", responseCode = "200"),
                ApiResponse(
                    description =
                        "The application was in the wrong status or " +
                            "there wasn't an open information request on the application.",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ])
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'EDIT_APPLICATIONS')")
    fun create(@PathVariable id: Long): TaydennysResponse =
        taydennysService.create(id, currentUserId()).toResponse()

    @RequestMapping("/taydennykset/{id}")
    @Operation(
        summary = "Update a täydennys",
        description =
            """
               Returns the updated täydennys.
               The täydennys can be updated until it has been sent to Allu.
               If the täydennys hasn't changed since the last update, nothing more is done.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The updated täydennys", responseCode = "200"),
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
                                            value = "{hankeError: 'HAI2008', errorPaths: ['name']}",
                                        ),
                                        ExampleObject(
                                            name = "Incompatible request",
                                            summary = "Incompatible request example",
                                            value = "{hankeError: 'HAI2002'}",
                                        ),
                                    ],
                            )],
                ),
                ApiResponse(
                    description = "A täydennys was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ])
    @PreAuthorize("@taydennysAuthorizer.authorize(#id, 'EDIT_APPLICATIONS')")
    fun update(
        @PathVariable id: UUID,
        @ValidHakemusUpdateRequest @RequestBody request: HakemusUpdateRequest,
    ): TaydennysResponse =
        taydennysService.updateTaydennys(id, request, currentUserId()).toResponse()

    @ExceptionHandler(InvalidHakemusDataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusDataException(ex: InvalidHakemusDataException): HankeErrorDetail {
        logger.warn(ex) { ex.message }
        return HankeErrorDetail(hankeError = HankeError.HAI2008, errorPaths = ex.errorPaths)
    }

    @ExceptionHandler(NoTaydennyspyyntoException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun noTaydennyspyyntoException(ex: NoTaydennyspyyntoException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2015
    }

    @ExceptionHandler(TaydennysNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun taydennysNotFoundException(ex: TaydennysNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI6001
    }

    @ExceptionHandler(IncompatibleTaydennysUpdateException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun incompatibleTaydennysUpdateException(ex: IncompatibleTaydennysUpdateException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }
}
