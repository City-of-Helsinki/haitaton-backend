package fi.hel.haitaton.hanke.attachment.application

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadException
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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/hakemukset/{applicationId}/liitteet")
class ApplicationAttachmentController(
    private val applicationAttachmentService: ApplicationAttachmentService,
    private val permissionService: PermissionService,
    private val hankeService: HankeService,
    private val applicationService: ApplicationService,
) {

    @GetMapping
    @Operation(
        summary = "Get metadata from application attachments.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Application attachments.", responseCode = "200"),
                ApiResponse(
                    description = "Application not found.",
                    responseCode = "404",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(implementation = ApplicationNotFoundException::class)
                            )
                        ]
                ),
            ]
    )
    fun getApplicationAttachments(
        @PathVariable applicationId: Long
    ): List<ApplicationAttachmentMetadata> {
        permissionOrThrow(applicationId, VIEW)
        return applicationAttachmentService.getMetadataList(applicationId)
    }

    @GetMapping("/{attachmentId}/content")
    @Operation(summary = "Download attachment file.")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Attachment file.", responseCode = "200"),
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
    fun getApplicationAttachmentContent(
        @PathVariable applicationId: Long,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        permissionOrThrow(applicationId, VIEW)
        val content = applicationAttachmentService.getContent(applicationId, attachmentId)

        return ResponseEntity.ok()
            .headers(buildHeaders(content.fileName, content.contentType))
            .body(content.bytes)
    }

    @PostMapping
    @Operation(
        summary = "Upload attachment for application",
        description =
            "Upload attachment. Sent to Allu if application has been sent but it is not yet in handling."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success.", responseCode = "200"),
                ApiResponse(
                    description = "Application not found.",
                    responseCode = "404",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(implementation = ApplicationNotFoundException::class)
                            )
                        ]
                ),
                ApiResponse(
                    description = "Attachment invalid.",
                    responseCode = "400",
                    content =
                        [
                            Content(
                                schema = Schema(implementation = AttachmentUploadException::class)
                            )
                        ]
                ),
                ApiResponse(
                    description = "Application already processing.",
                    responseCode = "409",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            ApplicationAlreadyProcessingException::class
                                    )
                            )
                        ]
                ),
            ]
    )
    fun postAttachment(
        @PathVariable applicationId: Long,
        @RequestParam("tyyppi") tyyppi: ApplicationAttachmentType,
        @RequestParam("liite") attachment: MultipartFile
    ): ApplicationAttachmentMetadata {
        permissionOrThrow(applicationId, EDIT)
        return applicationAttachmentService.addAttachment(applicationId, tyyppi, attachment)
    }

    @DeleteMapping("/{attachmentId}")
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Delete attachment.", responseCode = "200"),
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
                ApiResponse(
                    description = "Application already processing.",
                    responseCode = "409",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(
                                        implementation =
                                            ApplicationAlreadyProcessingException::class
                                    )
                            )
                        ]
                ),
            ]
    )
    fun removeAttachment(@PathVariable applicationId: Long, @PathVariable attachmentId: UUID) {
        permissionOrThrow(applicationId, EDIT)
        return applicationAttachmentService.deleteAttachment(applicationId, attachmentId)
    }

    fun permissionOrThrow(applicationId: Long, permissionCode: PermissionCode) {
        val userId = currentUserId()
        val application = applicationService.getApplicationById(applicationId)
        val hankeId = hankeService.getHankeId(application.hankeTunnus)
        if (hankeId == null || !permissionService.hasPermission(hankeId, userId, permissionCode)) {
            throw ApplicationNotFoundException(applicationId)
        }
    }
}
