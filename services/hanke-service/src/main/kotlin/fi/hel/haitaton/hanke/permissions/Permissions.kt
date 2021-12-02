package fi.hel.haitaton.hanke.permissions

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import javax.persistence.*

enum class PermissionCode(val code: Long) {
    VIEW(1),
    EDIT(2),

    ADD_VIEW(4),
    ADD_EDIT(8),

    REMOVE_VIEW(16),
    REMOVE_EDIT(32),

    SET_OWNER(64)
}

@Service
class PermissionService(private val repo : PermissionRepository) {

    fun getPermissionsByUserId(userId: String): List<Permission> = repo.findAllByUserId(userId).map(::entityToDto)

    fun getPermissionByHankeIdAndUserId(hankeId: Int, userId: String): Permission? {
        return repo.findOneByHankeIdAndUserId(hankeId, userId)
                ?.let { entityToDto(it) }
    }

    fun getPermissionById(id: Int): Permission? {
        return repo.findById(id)
                .map(::entityToDto)
                .orElse(null)
    }

    fun setPermission(hankeId: Int, userId: String, permissions: List<PermissionCode>): Permission {
        val permissionCode = combinePermissionCodes(permissions)
        val entity = repo.findOneByHankeIdAndUserId(hankeId, userId)
                ?: repo.save(PermissionEntity(userId, hankeId, permissionCode))
        return entityToDto(entity)
    }

    internal fun combinePermissionCodes(codes: List<PermissionCode>) : Long =
            codes.fold(0) { mask, permission -> mask and permission.code }

    internal fun permissionCodeToCodes(code: Long) : List<PermissionCode> =
            PermissionCode.values().filter { it.code and code == 0L }

    internal fun entityToDto(e: PermissionEntity) =
            Permission(e.id, e.userId, e.hankeId, permissionCodeToCodes(e.permissionCode))

}

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, Int> {
    fun findOneByHankeIdAndUserId(hankeId: Int, userId: String): PermissionEntity?
    fun findAllByUserId(userId: String): List<PermissionEntity>
}

data class Permission(
        val id: Int,
        val userId: String,
        val hankeId: Int,
        val permissions: List<PermissionCode>)

@Entity
@Table(name = "permissions")
class PermissionEntity(
        val userId: String,
        val hankeId: Int,
        var permissionCode: Long,
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Int = 0)

