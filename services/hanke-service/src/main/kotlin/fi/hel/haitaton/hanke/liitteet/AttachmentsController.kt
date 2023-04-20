package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.swagger.v3.oas.annotations.Operation
import java.net.URLConnection
import java.util.UUID
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/liitteet")
class AttachmentsController(
    @Autowired private val attachmentService: AttachmentService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
) {

    @GetMapping("/{uuid}")
    @Operation(
        summary = "Get attachment metadata by UUID",
        description =
            """
                Return information about an attachment. Available even when scan is pending.
            """
    )
    fun getAttachment(@PathVariable uuid: UUID): AttachmentMetadata {
        val currentUserId = currentUserId()
        val info = attachmentService.get(uuid)
        val hankeId = hankeService.getHankeId(info.hankeTunnus)

        if (
            hankeId == null ||
                !permissionService.hasPermission(hankeId, currentUserId, PermissionCode.VIEW)
        ) {
            throw HankeNotFoundException("")
        }

        return info
    }

    @GetMapping("/{uuid}/content")
    @Operation(
        summary = "Download attachment file.",
        description =
            """
                Returns the uploaded file. Only available after succesful virus scan.
            """
    )
    fun getAttachmentContent(
        @PathVariable uuid: UUID,
        response: HttpServletResponse
    ): HttpEntity<ByteArray> {
        val currentUserId = currentUserId()
        val info = attachmentService.get(uuid)
        val hankeId = hankeService.getHankeId(info.hankeTunnus)

        if (
            hankeId == null ||
                !permissionService.hasPermission(hankeId, currentUserId, PermissionCode.VIEW)
        ) {
            throw HankeNotFoundException("")
        }

        val metadata = attachmentService.get(uuid)
        val mimeType = URLConnection.guessContentTypeFromName(metadata.name)

        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(mimeType)
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + metadata.name)

        val file = attachmentService.getContent(uuid)
        return HttpEntity<ByteArray>(file, headers)
    }

    @DeleteMapping("/{uuid}")
    @Operation(
        summary = "Remove an attachment",
        description =
            """
                Removes an attachment and the related connections.
            """
    )
    fun removeAttachment(@PathVariable uuid: UUID) {
        val currentUserId = currentUserId()
        val info = attachmentService.get(uuid)
        val hankeId = hankeService.getHankeId(info.hankeTunnus)

        if (
            hankeId == null ||
                !permissionService.hasPermission(hankeId, currentUserId, PermissionCode.EDIT)
        ) {
            throw HankeNotFoundException("")
        }

        return attachmentService.removeAttachment(uuid)
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(HankeNotFoundException::class)
    fun handleArgumentExceptions(ex: HankeNotFoundException): HankeError {
        return HankeError.HAI1001
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(AttachmentNotFoundException::class)
    fun handleArgumentExceptions(ex: AttachmentNotFoundException): HankeError {
        return HankeError.HAI3002
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(AttachmentUploadError::class)
    fun handleArgumentExceptions(ex: AttachmentUploadError): HankeError {
        return HankeError.HAI3001
    }
}
