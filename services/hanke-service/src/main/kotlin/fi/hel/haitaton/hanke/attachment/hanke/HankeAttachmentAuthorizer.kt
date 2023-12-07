package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class HankeAttachmentAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val attachmentRepository: HankeAttachmentRepository,
) : Authorizer(permissionService, hankeRepository) {
    @Transactional(readOnly = true)
    fun authorizeAttachment(
        hankeTunnus: String,
        attachmentId: UUID,
        permissionCode: String
    ): Boolean {
        val permission = PermissionCode.valueOf(permissionCode)

        authorizeHankeTunnus(hankeTunnus, permission)
        if (findHanketunnusForAttachment(attachmentId) == hankeTunnus) return true
        throw AttachmentNotFoundException(attachmentId)
    }

    private fun findHanketunnusForAttachment(attachmentId: UUID): String? =
        attachmentRepository.findByIdOrNull(attachmentId)?.hanke?.hankeTunnus
}
