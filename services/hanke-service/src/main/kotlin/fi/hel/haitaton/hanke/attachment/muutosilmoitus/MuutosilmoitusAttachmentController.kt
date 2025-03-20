package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.HeadersBuilder.buildHeaders
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusNotFoundException
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/muutosilmoitukset/{muutosilmoitusId}/liitteet")
@SecurityRequirement(name = "bearerAuth")
class MuutosilmoitusAttachmentController(
    private val attachmentService: MuutosilmoitusAttachmentService
) {
    @GetMapping("/{attachmentId}/content")
    @Operation(
        summary = "Download attachment file",
        description =
            """
               Download the content for the given attachment.

               If the attachment is a power of attorney (valtakirja), it can't
               be downloaded because of privacy concerns.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Attachment file", responseCode = "200"),
                ApiResponse(
                    description = "Attachment not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description = "Attachment is valtakirja",
                    responseCode = "403",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    @PreAuthorize(
        "@muutosilmoitusAuthorizer.authorizeAttachment(#muutosilmoitusId, #attachmentId, 'VIEW')"
    )
    fun getApplicationAttachmentContent(
        @PathVariable muutosilmoitusId: UUID,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        val content = attachmentService.getContent(attachmentId)

        return ResponseEntity.ok()
            .headers(buildHeaders(content.fileName, content.contentType))
            .body(content.bytes)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload attachment for muutosilmoitus", description = "Upload attachment.")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(
                    description = "Muutosilmoitus not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description = "Attachment invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description = "Muutosilmoitus has been sent",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    @PreAuthorize("@muutosilmoitusAuthorizer.authorize(#muutosilmoitusId, 'EDIT_APPLICATIONS')")
    fun postAttachment(
        @PathVariable muutosilmoitusId: UUID,
        @RequestParam("tyyppi") tyyppi: ApplicationAttachmentType,
        @RequestParam("liite") attachment: MultipartFile,
    ): MuutosilmoitusAttachmentMetadataDto {
        return attachmentService.addAttachment(muutosilmoitusId, tyyppi, attachment).toDto()
    }

    @ExceptionHandler(MuutosilmoitusNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun muutosilmoitusNotFoundException(ex: MuutosilmoitusNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI7001
    }
}
