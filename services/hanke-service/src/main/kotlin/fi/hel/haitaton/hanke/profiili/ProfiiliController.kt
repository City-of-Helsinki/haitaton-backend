package fi.hel.haitaton.hanke.profiili

import fi.hel.haitaton.hanke.HankeError
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/profiili")
@SecurityRequirement(name = "bearerAuth")
class ProfiiliController(private val profiiliService: ProfiiliService) {

    @GetMapping("/verified-name")
    @Operation(summary = "Get verified name from Profiili")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(
                    description = "Not authorized.",
                    responseCode = "401",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description = "Verification not found.",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    fun verifiedName(
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext
    ): Names = profiiliService.getVerifiedName(securityContext)

    @ExceptionHandler(UnauthorizedException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @Hidden
    fun unauthorized(ex: UnauthorizedException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI4008
    }

    @ExceptionHandler(VerifiedNameNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun verifiedNameNotFound(ex: VerifiedNameNotFound): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI4005
    }

    @ExceptionHandler(NameClaimNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun claimNameNotFound(ex: NameClaimNotFound): HankeError {
        logger.error { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI4005
    }
}
