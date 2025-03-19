package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentRepository
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MuutosilmoitusAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val hakemusAuthorizer: HakemusAuthorizer,
    private val attachmentRepository: MuutosilmoitusAttachmentRepository,
) : Authorizer(permissionService, hankeRepository) {

    @Transactional(readOnly = true)
    fun authorize(id: UUID, permissionCode: String): Boolean {
        val muutosilmoitus =
            muutosilmoitusRepository.findByIdOrNull(id) ?: throw MuutosilmoitusNotFoundException(id)

        return hakemusAuthorizer.authorizeHakemusId(muutosilmoitus.hakemusId, permissionCode)
    }

    @Transactional(readOnly = true)
    fun authorizeAttachment(
        muutosilmoitusId: UUID,
        attachmentId: UUID,
        permissionCode: String,
    ): Boolean {
        authorize(muutosilmoitusId, permissionCode)
        return findMuutosilmoitusIdForAttachment(attachmentId) == muutosilmoitusId ||
            throw AttachmentNotFoundException(attachmentId)
    }

    private fun findMuutosilmoitusIdForAttachment(attachmentId: UUID): UUID? =
        attachmentRepository.findByIdOrNull(attachmentId)?.muutosilmoitusId
}
