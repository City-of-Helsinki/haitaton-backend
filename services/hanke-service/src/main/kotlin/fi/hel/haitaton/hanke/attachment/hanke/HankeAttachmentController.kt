package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.HeadersBuilder.buildHeaders
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
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
class HankeAttachmentController(
    private val hankeAttachmentService: HankeAttachmentService,
    private val hankeService: HankeService,
    private val permissionService: PermissionService,
) {

    @GetMapping
    @Operation(summary = "Get metadata from hanke attachments.")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Metadata of hanke attachments.", responseCode = "200"),
                ApiResponse(
                    description = "Hanke not found",
                    responseCode = "404",
                    content =
                        [Content(schema = Schema(implementation = HankeNotFoundException::class))]
                ),
            ]
    )
    fun getMetadataList(@PathVariable hankeTunnus: String): List<HankeAttachmentMetadata> {
        permissionOrThrow(hankeTunnus, VIEW)
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
                    content =
                        [
                            Content(
                                schema = Schema(implementation = AttachmentNotFoundException::class)
                            )
                        ]
                ),
            ]
    )
    fun getAttachmentContent(
        @PathVariable hankeTunnus: String,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        permissionOrThrow(hankeTunnus, VIEW)
        val content = hankeAttachmentService.getContent(hankeTunnus, attachmentId)

        return ResponseEntity.ok()
            .headers(buildHeaders(content.fileName, content.contentType))
            .body(content.bytes)
    }

    @PostMapping
    @Operation(summary = "Upload an attachment for Hanke")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success.",
                    responseCode = "200",
                    content =
                        [Content(schema = Schema(implementation = HankeAttachmentMetadata::class))]
                ),
                ApiResponse(
                    description = "Hanke not found.",
                    responseCode = "404",
                    content =
                        [Content(schema = Schema(implementation = HankeNotFoundException::class))]
                ),
                ApiResponse(
                    description = "Invalid attachment.",
                    responseCode = "400",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = AttachmentUploadException::class)
                            )
                        ]
                ),
            ]
    )
    fun postAttachment(
        @PathVariable hankeTunnus: String,
        @RequestParam("liite") attachment: MultipartFile
    ): HankeAttachmentMetadata {
        permissionOrThrow(hankeTunnus, EDIT)
        return hankeAttachmentService.addAttachment(hankeTunnus, attachment)
    }

    @DeleteMapping("/{attachmentId}")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Removes the attachment.", responseCode = "200"),
                ApiResponse(
                    description = "Attachment was not found by id",
                    responseCode = "404",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = AttachmentNotFoundException::class)
                            )
                        ]
                ),
            ]
    )
    fun deleteAttachment(@PathVariable hankeTunnus: String, @PathVariable attachmentId: UUID) {
        permissionOrThrow(hankeTunnus, EDIT)
        return hankeAttachmentService.deleteAttachment(hankeTunnus, attachmentId)
    }

    private fun permissionOrThrow(hankeTunnus: String, permissionCode: PermissionCode) {
        val userId = currentUserId()
        val hankeId =
            hankeService.getHankeId(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (!permissionService.hasPermission(hankeId, userId, permissionCode)) {
            logger.warn { "User $userId has no permission $permissionCode for hanke: $hankeId" }
            throw HankeNotFoundException(hankeTunnus)
        }
    }
}
