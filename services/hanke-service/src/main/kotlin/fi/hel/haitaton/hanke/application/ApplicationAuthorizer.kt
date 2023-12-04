package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeRepository
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
class ApplicationAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val applicationRepository: ApplicationRepository,
    private val attachmentRepository: ApplicationAttachmentRepository,
) : Authorizer(permissionService, hankeRepository) {
    private fun authorizeApplicationId(
        applicationId: Long,
        permissionCode: PermissionCode
    ): Boolean {
        val hankeId = applicationRepository.findOneById(applicationId)?.hanke?.id
        authorize(hankeId, permissionCode) { ApplicationNotFoundException(applicationId) }
        return true
    }

    @Transactional(readOnly = true)
    fun authorizeApplicationId(applicationId: Long, permissionCode: String): Boolean =
        authorizeApplicationId(applicationId, PermissionCode.valueOf(permissionCode))

    @Transactional(readOnly = true)
    fun authorizeCreate(application: Application): Boolean =
        authorizeHankeTunnus(application.hankeTunnus, PermissionCode.EDIT_APPLICATIONS)

    @Transactional(readOnly = true)
    fun authorizeAttachment(
        applicationId: Long,
        attachmentId: UUID,
        permissionCode: String
    ): Boolean {
        authorizeApplicationId(applicationId, permissionCode)
        return findApplicationIdForAttachment(attachmentId) == applicationId ||
            throw AttachmentNotFoundException(attachmentId)
    }

    private fun findApplicationIdForAttachment(attachmentId: UUID): Long? =
        attachmentRepository.findByIdOrNull(attachmentId)?.applicationId
}
