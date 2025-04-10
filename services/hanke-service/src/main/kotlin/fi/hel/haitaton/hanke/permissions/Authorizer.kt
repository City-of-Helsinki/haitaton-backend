package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeAlreadyCompletedException
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.domain.HankeStatus
import org.springframework.transaction.annotation.Transactional

abstract class Authorizer(
    private val permissionService: PermissionService,
    private val hankeRepository: HankeRepository,
) {
    fun authorize(
        hankeId: Int?,
        permissionCode: PermissionCode,
        editDeniedForCompleted: Boolean = true,
        ex: () -> Exception,
    ) {
        if (hankeId == null) throw ex()
        if (!permissionService.hasPermission(hankeId, currentUserId(), permissionCode)) {
            throw ex()
        }

        val hankeStatus = hankeRepository.findStatusById(hankeId)!!
        if (
            editDeniedForCompleted &&
                hankeStatus == HankeStatus.COMPLETED &&
                permissionCode != PermissionCode.VIEW
        ) {
            throw HankeAlreadyCompletedException(hankeId)
        }
    }

    internal fun authorizeHankeTunnus(
        hankeTunnus: String,
        permissionCode: PermissionCode,
    ): Boolean {
        val hankeIdentifier = hankeRepository.findOneByHankeTunnus(hankeTunnus)
        authorize(hankeIdentifier?.id, permissionCode) { HankeNotFoundException(hankeTunnus) }
        return true
    }

    @Transactional(readOnly = false)
    open fun authorizeHankeTunnus(hankeTunnus: String, permissionCode: String): Boolean =
        authorizeHankeTunnus(hankeTunnus, PermissionCode.valueOf(permissionCode))
}
