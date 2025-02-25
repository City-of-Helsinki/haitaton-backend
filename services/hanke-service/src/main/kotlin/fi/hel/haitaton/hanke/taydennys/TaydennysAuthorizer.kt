package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentRepository
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TaydennysAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val taydennysRepository: TaydennysRepository,
    private val hakemusAuthorizer: HakemusAuthorizer,
    private val attachmentRepository: TaydennysAttachmentRepository,
) : Authorizer(permissionService, hankeRepository) {

    @Transactional(readOnly = true)
    fun authorize(id: UUID, permissionCode: String): Boolean {
        val taydennys =
            taydennysRepository.findByIdOrNull(id) ?: throw TaydennysNotFoundException(id)

        return taydennys.taydennyspyynto.applicationId.let {
            hakemusAuthorizer.authorizeHakemusId(it, permissionCode)
        }
    }

    @Transactional(readOnly = true)
    fun authorizeAttachment(
        taydennysId: UUID,
        attachmentId: UUID,
        permissionCode: String,
    ): Boolean {
        authorize(taydennysId, permissionCode)
        return findTaydennysIdForAttachment(attachmentId) == taydennysId ||
            throw AttachmentNotFoundException(attachmentId)
    }

    private fun findTaydennysIdForAttachment(attachmentId: UUID): UUID? =
        attachmentRepository.findByIdOrNull(attachmentId)?.taydennysId
}
