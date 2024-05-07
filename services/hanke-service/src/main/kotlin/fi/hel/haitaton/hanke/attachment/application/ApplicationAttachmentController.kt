package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.HeadersBuilder.buildHeaders
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
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
import org.springframework.web.bind.annotation.DeleteMapping
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
@RequestMapping("/hakemukset/{applicationId}/liitteet")
@SecurityRequirement(name = "bearerAuth")
class ApplicationAttachmentController(
    private val applicationAttachmentService: ApplicationAttachmentService,
) {

    @GetMapping
    @Operation(
        summary = "Get metadata from application attachments",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Application attachments", responseCode = "200"),
                ApiResponse(
                    description = "Application not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#applicationId, 'VIEW')")
    fun getApplicationAttachments(
        @PathVariable applicationId: Long
    ): List<ApplicationAttachmentMetadataDto> {
        return applicationAttachmentService.getMetadataList(applicationId).map { it.toDto() }
    }

    @GetMapping("/{attachmentId}/content")
    @Operation(summary = "Download attachment file")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Attachment file", responseCode = "200"),
                ApiResponse(
                    description = "Attachment not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize(
        "@applicationAuthorizer.authorizeAttachment(#applicationId, #attachmentId, 'VIEW')"
    )
    fun getApplicationAttachmentContent(
        @PathVariable applicationId: Long,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        val content = applicationAttachmentService.getContent(attachmentId)

        return ResponseEntity.ok()
            .headers(buildHeaders(content.fileName, content.contentType))
            .body(content.bytes)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload attachment for application",
        description =
            "Upload attachment. Sent to Allu if application has been sent but it is not yet in handling."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(
                    description = "Application not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Attachment invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Application already in Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize(
        "@applicationAuthorizer.authorizeApplicationId(#applicationId, 'EDIT_APPLICATIONS')"
    )
    fun postAttachment(
        @PathVariable applicationId: Long,
        @RequestParam("tyyppi") tyyppi: ApplicationAttachmentType,
        @RequestParam("liite") attachment: MultipartFile
    ): ApplicationAttachmentMetadataDto {
        return applicationAttachmentService.addAttachment(applicationId, tyyppi, attachment)
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(
        summary = "Delete attachment from application",
        description =
            "Can be deleted if application has not been sent to Allu. Don't delete if application has alluId."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Delete attachment", responseCode = "200"),
                ApiResponse(
                    description = "Attachment not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Application already in Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize(
        "@applicationAuthorizer.authorizeAttachment(#applicationId, #attachmentId, 'EDIT_APPLICATIONS')"
    )
    fun removeAttachment(@PathVariable applicationId: Long, @PathVariable attachmentId: UUID) {
        logger.info { "Deleting attachment $attachmentId from application $applicationId." }
        return applicationAttachmentService.deleteAttachment(attachmentId)
    }

    @ExceptionHandler(ApplicationInAlluException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun alluDataError(ex: ApplicationInAlluException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2009
    }

    @ExceptionHandler(ValtakirjaForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @Hidden
    fun valtakirjaForbiddenException(ex: ValtakirjaForbiddenException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI3004
    }
}
