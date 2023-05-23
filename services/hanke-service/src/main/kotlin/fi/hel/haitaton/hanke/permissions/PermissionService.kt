package fi.hel.haitaton.hanke.permissions

import org.springframework.stereotype.Service

@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val roleRepository: RoleRepository
) {
    fun getAllowedHankeIds(userId: String, permission: PermissionCode): List<Int> =
        permissionRepository.findAllByUserIdAndPermission(userId, permission.code).map {
            it.hankeId
        }

    fun hasPermission(hankeId: Int, userId: String, permission: PermissionCode): Boolean {
        val role = permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.role
        return hasPermission(role, permission)
    }

    fun setPermission(hankeId: Int, userId: String, role: Role) {
        val roleEntity = roleRepository.findOneByRole(role)
        val entity =
            permissionRepository.findOneByHankeIdAndUserId(hankeId, userId)?.apply {
                this.role = roleEntity
            }
                ?: PermissionEntity(userId = userId, hankeId = hankeId, role = roleEntity)
        permissionRepository.save(entity)
    }

    companion object {
        fun hasPermission(role: RoleEntity?, permission: PermissionCode): Boolean =
            (role?.permissionCode ?: 0) and permission.code > 0
    }
}
