package fi.hel.haitaton.hanke.paatos

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/paatokset")
@SecurityRequirement(name = "bearerAuth")
class PaatosController(
    private val paatosService: PaatosService,
    private val disclosureLogService: DisclosureLogService,
) {
    @GetMapping("/{id}")
    @Operation(
        summary = "Download decision with the given ID.",
        description =
            """
                Downloads the decision PDF for this decision. Decision can also be an approval
                document for an operational condition or a work finished.
            """)
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "The decision PDF",
                    responseCode = "200",
                    content = [Content(mediaType = MediaType.APPLICATION_PDF_VALUE)],
                ),
                ApiResponse(
                    description = "A decision was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ])
    @PreAuthorize("@paatosAuthorizer.authorizePaatosId(#id, 'VIEW')")
    fun download(@PathVariable("id") id: UUID): ResponseEntity<ByteArray> {
        val paatos: Paatos = paatosService.findById(id)
        val (filename, pdfBytes) = paatosService.downloadDecision(paatos)
        disclosureLogService.saveDisclosureLogsForPaatos(paatos.toMetadata(), currentUserId())

        val headers = HttpHeaders()
        headers.add("Content-Disposition", "inline; filename=$filename")
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
    }

    @ExceptionHandler(PaatosNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun paatosNotFoundException(ex: PaatosNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI5001
    }
}
