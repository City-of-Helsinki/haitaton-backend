package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
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
import java.net.URLConnection
import java.util.UUID
import javax.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.MediaType
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
@RequestMapping
class AttachmentController(
    @Autowired private val attachmentService: AttachmentService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
) {

    @GetMapping("liitteet/{id}")
    @Operation(
        summary = "Get attachment metadata by UUID",
        description = "Return information about an attachment without binary data."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Get attachment by Id.",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = AttachmentMetadata::class))]
                ),
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
    fun getAttachment(@PathVariable id: UUID): AttachmentMetadata {
        val attachmentMetadata = attachmentService.getMetadata(id)
        val hankeId = hankeService.getHankeId(attachmentMetadata.hankeTunnus)

        checkAttachmentPermission(hankeId, currentUserId(), VIEW)

        return attachmentMetadata
    }

    @GetMapping("liitteet/{id}/content")
    @Operation(summary = "Download attachment file.", description = "Returns the uploaded file.")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Download content of the attachment file.",
                    responseCode = "200"
                ),
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
        @PathVariable id: UUID,
        response: HttpServletResponse
    ): ResponseEntity<ByteArray> {
        val attachmentMetadata = attachmentService.getMetadata(id)
        val hankeId = hankeService.getHankeId(attachmentMetadata.hankeTunnus)

        checkAttachmentPermission(hankeId, currentUserId(), VIEW)

        val metadata = attachmentService.getMetadata(id)
        val mimeType = URLConnection.guessContentTypeFromName(metadata.fileName)

        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(mimeType)
        headers.add(CONTENT_DISPOSITION, "attachment; filename=${metadata.fileName}")

        val file = attachmentService.getContent(id)
        return ResponseEntity.ok().headers(headers).body(file)
    }

    @DeleteMapping("liitteet/{id}")
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
    fun removeAttachment(@PathVariable id: UUID) {
        val info = attachmentService.getMetadata(id)
        val hankeId = hankeService.getHankeId(info.hankeTunnus)

        checkAttachmentPermission(hankeId, currentUserId(), EDIT)

        return attachmentService.removeAttachment(id)
    }

    @GetMapping("/hankkeet/{hankeTunnus}/liitteet")
    @Operation(summary = "Get attachments related to Hanke")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description =
                        "Attachments of hanke. Note: does not list attachments of applications.",
                    responseCode = "200"
                ),
                ApiResponse(
                    description = "Hanke not found",
                    responseCode = "404",
                    content =
                        [Content(schema = Schema(implementation = HankeNotFoundException::class))]
                ),
            ]
    )
    fun getAttachments(@PathVariable hankeTunnus: String): List<AttachmentMetadata> {
        val hankeId = hankeService.getHankeId(hankeTunnus)

        checkHankePermission(hankeId, hankeTunnus, currentUserId(), VIEW)

        return attachmentService.getHankeAttachments(hankeTunnus)
    }

    @PostMapping("/hankkeet/{hankeTunnus}/liitteet")
    @Operation(summary = "Upload an attachment for Hanke")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Upload attachment to hanke",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = AttachmentMetadata::class))]
                ),
                ApiResponse(
                    description = "Hanke not found.",
                    responseCode = "404",
                    content =
                        [Content(schema = Schema(implementation = HankeNotFoundException::class))]
                ),
                ApiResponse(
                    description = "Attachment could not be uploaded",
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
    ): AttachmentMetadata {
        val hankeId =
            hankeService.getHankeId(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        checkHankePermission(hankeId, hankeTunnus, currentUserId(), EDIT)

        return attachmentService.add(hankeTunnus, attachment)
    }

    private fun checkAttachmentPermission(
        hankeId: Int?,
        userId: String,
        permissionCode: PermissionCode
    ) {
        if (hankeId == null || !permissionService.hasPermission(hankeId, userId, permissionCode)) {
            logger.warn { "User $userId has no permission $permissionCode for hanke: $hankeId" }
            throw AttachmentNotFoundException()
        }
    }

    private fun checkHankePermission(
        hankeId: Int?,
        hankeTunnus: String,
        userId: String,
        permissionCode: PermissionCode
    ) {
        if (hankeId == null || !permissionService.hasPermission(hankeId, userId, permissionCode)) {
            logger.warn { "User $userId has no permission $permissionCode for hanke: $hankeId" }
            throw HankeNotFoundException(hankeTunnus)
        }
    }
}
