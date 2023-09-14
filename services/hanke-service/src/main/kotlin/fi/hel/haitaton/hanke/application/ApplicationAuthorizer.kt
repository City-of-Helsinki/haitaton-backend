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
    @Transactional(readOnly = false)
    fun authorizeApplicationId(applicationId: Long, permissionCode: PermissionCode) {
        val hankeId = applicationRepository.findOneById(applicationId)?.hanke?.id
        authorize(hankeId, permissionCode) { ApplicationNotFoundException(applicationId) }
    }
}
