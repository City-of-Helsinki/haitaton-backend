package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.daysBetween
import fi.hel.haitaton.hanke.geometria.GeometriatValidator
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.time.ZonedDateTime
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/haittaindeksit")
@SecurityRequirement(name = "bearerAuth")
class TormaystarkasteluController(
    private val tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService,
) {

    @PostMapping
    @Operation(
        summary = "Calculate nuisance indices",
        description =
            "Calculate nuisance indices with the given geometry and other information. The calculated indices will not be saved anywhere.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description =
                        "The calculated nuisance indices for the given geometry and other information.",
                    responseCode = "200",
                ),
                ApiResponse(
                    description = "The given information was not valid.",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ],
    )
    fun calculate(@RequestBody request: TormaystarkasteluRequest): TormaystarkasteluTulos {
        GeometriatValidator.expectValid(request.geometriat.featureCollection)

        if (request.haittaAlkuPvm.isAfter(request.haittaLoppuPvm)) {
            throw EndBeforeStartException(request.haittaAlkuPvm, request.haittaLoppuPvm)
        }

        val haittaajanKestoDays = daysBetween(request.haittaAlkuPvm, request.haittaLoppuPvm)!!

        return tormaystarkasteluLaskentaService.calculateTormaystarkastelu(
            request.geometriat.featureCollection,
            haittaajanKestoDays,
            request.kaistaHaitta,
            request.kaistaPituusHaitta,
        )
    }

    class EndBeforeStartException(start: ZonedDateTime, end: ZonedDateTime) :
        RuntimeException("Start date is after the end date. start=$start, end=$end")

    @ExceptionHandler(EndBeforeStartException::class)
    @Hidden
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun endBeforeStartException(ex: EndBeforeStartException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }
}
