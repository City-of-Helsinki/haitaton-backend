package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.springframework.context.annotation.Primary
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Primary
class HakemusAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
) : Authorizer(permissionService, hankeRepository) {
    private fun authorizeHakemusId(hakemusId: Long, permissionCode: PermissionCode): Boolean {
        val hankeId = applicationRepository.findOneById(hakemusId)?.hanke?.id
        authorize(hankeId, permissionCode) { ApplicationNotFoundException(hakemusId) }
        return true
    }

    @Transactional(readOnly = true)
    fun authorizeHakemusId(hakemusId: Long, permissionCode: String): Boolean =
        authorizeHakemusId(hakemusId, PermissionCode.valueOf(permissionCode))

    @Transactional(readOnly = true)
    fun authorizeCreate(hakemus: CreateHakemusRequest): Boolean =
        authorizeHankeTunnus(hakemus.hankeTunnus, PermissionCode.EDIT_APPLICATIONS)

    @Transactional(readOnly = true)
    fun authorizeAttachment(hakemusId: Long, attachmentId: UUID, permissionCode: String): Boolean {
        authorizeHakemusId(hakemusId, permissionCode)
        return findHakemusIdForAttachment(attachmentId) == hakemusId ||
            throw AttachmentNotFoundException(attachmentId)
    }

    private fun findHakemusIdForAttachment(attachmentId: UUID): Long? =
        attachmentRepository.findByIdOrNull(attachmentId)?.applicationId
}
