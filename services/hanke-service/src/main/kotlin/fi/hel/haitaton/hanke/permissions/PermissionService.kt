package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.stereotype.Service

@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository
) {
    fun findByHankeId(hankeId: Int) = permissionRepository.findAllByHankeId(hankeId)

    fun getAllowedHankeIds(userId: String, permission: PermissionCode): List<Int> =
        permissionRepository.findAllByUserIdAndPermission(userId, permission.code).map {
            it.hankeId
        }

    fun hasPermission(hankeId: Int, userId: String, permission: PermissionCode): Boolean {
        val role = permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.role
        return hasPermission(role, permission)
    }

    fun setPermission(hankeId: Int, userId: String, role: Role): PermissionEntity {
        val roleEntity = roleRepository.findOneByRole(role)
        val entity =
            permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.apply {
                this.role = roleEntity
            }
                ?: PermissionEntity(userId = userId, hankeId = hankeId, role = roleEntity)
        return permissionRepository.save(entity)
    }

    fun verifyHankeUserAuthorization(userId: String, hanke: Hanke, permissionCode: PermissionCode) {
        val hankeId = hanke.id
        if (hankeId == null || !hasPermission(hankeId, userId, permissionCode)) {
            throw HankeNotFoundException(hanke.hankeTunnus)
        }
    }

    companion object {
        fun hasPermission(role: RoleEntity?, permission: PermissionCode): Boolean =
            (role?.permissionCode ?: 0) and permission.code > 0
    }
}
