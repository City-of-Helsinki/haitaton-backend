package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ApplicationAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val applicationRepository: ApplicationRepository,
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
}
