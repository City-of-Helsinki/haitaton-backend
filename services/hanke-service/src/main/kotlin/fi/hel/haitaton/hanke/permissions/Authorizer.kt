package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
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

    internal fun authorizeHankeTunnus(
        hankeTunnus: String,
        permissionCode: PermissionCode
    ): Boolean {
        val hankeIdentifier = hankeRepository!!.findOneByHankeTunnus(hankeTunnus)
        authorize(hankeIdentifier?.id, permissionCode) { HankeNotFoundException(hankeTunnus) }
        return true
    }

    @Transactional(readOnly = false)
    open fun authorizeHankeTunnus(hankeTunnus: String, permissionCode: String): Boolean =
        authorizeHankeTunnus(hankeTunnus, PermissionCode.valueOf(permissionCode))
}
