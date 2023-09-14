package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeIds
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

abstract class Authorizer(
    private val permissionService: PermissionService,
    private val hankeRepository: HankeRepository? = null
) {
    fun authorize(hankeId: Int?, permissionCode: PermissionCode, ex: () -> Exception) {
        hankeId
            ?.let { permissionService.hasPermission(it, currentUserId(), permissionCode) }
            .let { if (it != true) throw ex() }
    }

    @Transactional(readOnly = false)
    open fun authorizeHankeTunnus(hankeTunnus: String, permissionCode: PermissionCode): HankeIds {
        val hankeIds = hankeRepository!!.findOneByHankeTunnus(hankeTunnus)
        authorize(hankeIds?.id, permissionCode) { HankeNotFoundException(hankeTunnus) }
        return hankeIds!!
    }
}

@Component
class HankeAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
) : Authorizer(permissionService, hankeRepository)
