package fi.hel.haitaton.hanke.permissions

import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service

enum class PermissionCode(val code: Long) {
    VIEW(1),
    MODIFY_VIEW_PERMISSIONS(2),
    EDIT(4),
    MODIFY_EDIT_PERMISSIONS(8),
    DELETE(16),
    MODIFY_DELETE_PERMISSIONS(32),
    EDIT_APPLICATIONS(64),
    MODIFY_APPLICATION_PERMISSIONS(128)
}

@Service
class PermissionService(
    private val repo: PermissionRepository,
    private val roleRepository: RoleRepository
) {
    fun getAllowedHankeIds(userId: String, permission: PermissionCode): List<Int> =
        repo.findAllByUserIdAndPermission(userId, permission.code).map { it.hankeId }

    fun hasPermission(hankeId: Int, userId: String, permission: PermissionCode): Boolean {
        val role = repo.findOneByHankeIdAndUserId(hankeId, userId)?.role
        return hasPermission(role, permission)
    }

    fun setPermission(hankeId: Int, userId: String, role: Role) {
        val roleEntity = roleRepository.findOneByRole(role)
        val entity =
            repo.findOneByHankeIdAndUserId(hankeId, userId)?.apply { this.role = roleEntity }
                ?: PermissionEntity(userId = userId, hankeId = hankeId, role = roleEntity)
        repo.save(entity)
    }

    companion object {
        fun hasPermission(role: RoleEntity?, permission: PermissionCode): Boolean =
            (role?.permissionCode ?: 0) and permission.code > 0
    }
}

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, Int> {
    fun findOneByHankeIdAndUserId(hankeId: Int, userId: String): PermissionEntity?

    /**
     * Search for permissions with the given user and a single permission code. JPQL doesn't have
     * bitwise and, so we simulate it with a division (shift right) and mod. This only works when
     * [permissionBit] has exactly one bit set, i.e. the Long is a power of two.
     */
    @Query(
        "select pe from PermissionEntity pe " +
            "inner join pe.role as role " +
            "where pe.userId = :userId " +
            "and mod(role.permissionCode / :permissionBit , 2) = 1"
    )
    fun findAllByUserIdAndPermission(userId: String, permissionBit: Long): List<PermissionEntity>
}

@Entity
@Table(name = "permissions")
class PermissionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    val userId: String,
    val hankeId: Int,
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "roleid")
    var role: RoleEntity,
)
