package fi.hel.haitaton.hanke.attachment.taydennys

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/taydennykset/{taydennysId}/liitteet")
@SecurityRequirement(name = "bearerAuth")
class TaydennysAttachmentController(private val attachmentService: TaydennysAttachmentService) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload attachment for täydennys", description = "Upload attachment.")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(
                    description = "Täydennys not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description = "Attachment invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    @PreAuthorize("@taydennysAuthorizer.authorize(#taydennysId, 'EDIT_APPLICATIONS')")
    fun postAttachment(
        @PathVariable taydennysId: UUID,
        @RequestParam("tyyppi") tyyppi: ApplicationAttachmentType,
        @RequestParam("liite") attachment: MultipartFile,
    ): TaydennysAttachmentMetadataDto {
        return attachmentService.addAttachment(taydennysId, tyyppi, attachment)
    }

    @ExceptionHandler(ValtakirjaForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @Hidden
    fun valtakirjaForbiddenException(ex: ValtakirjaForbiddenException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI3004
    }
}
