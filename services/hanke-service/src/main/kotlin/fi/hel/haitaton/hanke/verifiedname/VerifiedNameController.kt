package fi.hel.haitaton.hanke.verifiedname

import fi.hel.haitaton.hanke.HankeError
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@SecurityRequirement(name = "bearerAuth")
class VerifiedNameController(private val verifiedNameService: VerifiedNameService) {

    @GetMapping("/verified-name")
    @Operation(summary = "Get the user's verified name")
    @ApiResponse(description = "Success", responseCode = "200")
    @ApiResponse(
        description = "Verification not found.",
        responseCode = "404",
        content = [Content(schema = Schema(implementation = HankeError::class))],
    )
    fun verifiedName(
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext
    ): Names = verifiedNameService.getVerifiedName(securityContext)

    // Kept working for existing frontend builds that still call the old path from when this was
    // backed by Helsinki Profiili. Remove once the UI has switched to GET /verified-name.
    @GetMapping("/profiili/verified-name")
    @Operation(summary = "Get the user's verified name (deprecated path)", deprecated = true)
    @ApiResponse(description = "Success", responseCode = "200")
    @ApiResponse(
        description = "Verification not found.",
        responseCode = "404",
        content = [Content(schema = Schema(implementation = HankeError::class))],
    )
    fun verifiedNameLegacyPath(
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext
    ): Names = verifiedNameService.getVerifiedName(securityContext)

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
