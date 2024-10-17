package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.currentUserId
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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

    @ExceptionHandler(NoTaydennyspyyntoException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun noTaydennyspyyntoException(ex: NoTaydennyspyyntoException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2015
    }
}
