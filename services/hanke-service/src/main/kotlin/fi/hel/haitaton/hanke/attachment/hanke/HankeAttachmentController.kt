package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.HeadersBuilder.buildHeaders
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hankkeet/{hankeTunnus}/liitteet")
@SecurityRequirement(name = "bearerAuth")
class HankeAttachmentController(
    private val hankeAttachmentService: HankeAttachmentService,
) {

    @GetMapping
    @Operation(summary = "Get metadata from hanke attachments")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Metadata of hanke attachments", responseCode = "200"),
                ApiResponse(
                    description = "Hanke not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@hankeAttachmentAuthorizer.authorizeHankeTunnus(#hankeTunnus,'VIEW')")
    fun getMetadataList(@PathVariable hankeTunnus: String): List<HankeAttachmentMetadataDto> {
        return hankeAttachmentService.getMetadataList(hankeTunnus)
    }

    @GetMapping("/{attachmentId}/content")
    @Operation(summary = "Download attachment file.")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success.", responseCode = "200"),
                ApiResponse(
                    description = "Attachment not found.",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize(
        "@hankeAttachmentAuthorizer.authorizeAttachment(#hankeTunnus,#attachmentId,'VIEW')"
    )
    fun getAttachmentContent(
        @PathVariable hankeTunnus: String,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        val content = hankeAttachmentService.getContent(attachmentId)

        return ResponseEntity.ok()
            .headers(buildHeaders(content.fileName, content.contentType))
            .body(content.bytes)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload an attachment for Hanke")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(
                    description = "Hanke not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Invalid attachment",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize(
        "@featureService.isEnabled('HANKE_EDITING') && " +
            "@hankeAttachmentAuthorizer.authorizeHankeTunnus(#hankeTunnus,'EDIT')"
    )
    fun postAttachment(
        @PathVariable hankeTunnus: String,
        @RequestParam("liite") attachment: MultipartFile,
    ): HankeAttachmentMetadataDto {
        logger.info {
            "Adding attachment to hanke, hankeTunnus = $hankeTunnus, " +
                "attachment name = ${attachment.originalFilename}, size = ${attachment.bytes.size}, " +
                "content type = ${attachment.contentType}"
        }

        return hankeAttachmentService.uploadHankeAttachment(hankeTunnus, attachment)
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(summary = "Delete attachment from hanke")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(
                    description = "Attachment not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                )
            ]
    )
    @PreAuthorize(
        "@featureService.isEnabled('HANKE_EDITING') && " +
            "@hankeAttachmentAuthorizer.authorizeAttachment(#hankeTunnus,#attachmentId,'EDIT')"
    )
    fun deleteAttachment(@PathVariable hankeTunnus: String, @PathVariable attachmentId: UUID) {
        hankeAttachmentService.deleteAttachment(attachmentId)
        logger.info { "Deleted hanke attachment $attachmentId" }
    }
}
