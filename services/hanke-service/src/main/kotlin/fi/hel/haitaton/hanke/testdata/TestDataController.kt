package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.profiili.Names
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.profiili.VerifiedNameNotFound
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/testdata")
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class TestDataController(
    private val testDataService: TestDataService,
    private val profiiliClient: ProfiiliClient
) {

    @EventListener(ApplicationReadyEvent::class)
    fun logWarning() {
        logger.warn { "Test data endpoint enabled." }
    }

    @Operation(
        summary = "Unlink applications from allu",
        description =
            "Removes the Allu IDs and statuses from all applications, effectively " +
                "unlinking them from Allu and returning them to drafts. The applications " +
                "can be re-sent to allu whenever. This endpoint can be used to unlink all " +
                "applications from Allu after Allu wipes all of it's data during test " +
                "environment update. Allu will re-issue the same IDs, causing collisions " +
                "in Haitaton."
    )
    @PostMapping("/unlink-applications")
    fun unlinkApplicationsFromAllu() {
        testDataService.unlinkApplicationsFromAllu()
        logger.warn { "Unlinked all applications from Allu." }
    }

    @GetMapping("/verified-name")
    @Operation(summary = "Get verified name from Profiili")
    @ApiResponse(description = "Success", responseCode = "200")
    @ApiResponse(
        description = "Verification not found.",
        responseCode = "404",
        content = [Content(schema = Schema(implementation = HankeError::class))]
    )
    @SecurityRequirement(name = "bearerAuth")
    fun verifiedName(
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext
    ): Names {
        return profiiliClient.getVerifiedName(securityContext)
            ?: throw VerifiedNameNotFound("Verified name not found from profile.")
    }

    @ExceptionHandler(VerifiedNameNotFound::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun verifiedNameNotFound(ex: VerifiedNameNotFound): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI4005
    }
}
