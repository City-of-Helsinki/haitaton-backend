package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/liitteet")
class AttachmentsController(
    @Autowired private val attachmentService: AttachmentService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
) {

    @GetMapping("/hanke/{hankeTunnus}")
    fun getAttachments(@PathVariable hankeTunnus: String): List<HankeAttachment> {
        val currentUserId = currentUserId()
        val hankeId =
            hankeService.getHankeId(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (!permissionService.hasPermission(hankeId, currentUserId, PermissionCode.VIEW)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        val hankeAttachments = attachmentService.getHankeAttachments(hankeTunnus)
        return hankeAttachments
    }

    @PostMapping("/hanke/{hankeTunnus}")
    fun postAttachment(
        @PathVariable hankeTunnus: String,
        @RequestParam("liite") attachment: MultipartFile
    ): HankeAttachment {
        val currentUserId = currentUserId()
        val hankeId =
            hankeService.getHankeId(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (!permissionService.hasPermission(hankeId, currentUserId, PermissionCode.EDIT)) {
            throw HankeNotFoundException(hankeTunnus)
        }

        val newAttachment = attachmentService.add(hankeTunnus, attachment)
        return newAttachment
    }

    @GetMapping("/{uuid}")
    fun getAttachment(@PathVariable uuid: UUID): HankeAttachment {
        val currentUserId = currentUserId()
        val info = attachmentService.get(uuid)

        if (
            info.hankeId == null ||
                !permissionService.hasPermission(info.hankeId!!, currentUserId, PermissionCode.VIEW)
        ) {
            throw HankeNotFoundException("")
        }

        return info
    }

    @GetMapping("/{uuid}/data")
    fun getAttachmentData(@PathVariable uuid: UUID): ByteArray {
        val currentUserId = currentUserId()
        val info = attachmentService.get(uuid)

        if (
            info.hankeId == null ||
                !permissionService.hasPermission(info.hankeId!!, currentUserId, PermissionCode.VIEW)
        ) {
            throw HankeNotFoundException("")
        }

        return attachmentService.getData(uuid)
    }

    @DeleteMapping("/{uuid}")
    fun removeAttachment(@PathVariable uuid: UUID) {
        val currentUserId = currentUserId()
        val info = attachmentService.get(uuid)

        if (
            info.hankeId == null ||
                !permissionService.hasPermission(info.hankeId!!, currentUserId, PermissionCode.EDIT)
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
