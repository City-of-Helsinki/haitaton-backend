package fi.hel.haitaton.hanke.permissions

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.domain.HasId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

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

@Repository
interface PermissionRepository : JpaRepository<PermissionEntity, Int> {
    fun findOneByHankeIdAndUserId(hankeId: Int, userId: String): PermissionEntity?

    fun findAllByHankeId(hankeId: Int): List<PermissionEntity>

    /**
     * Search for permissions with the given user and a single permission code. JPQL doesn't have
     * bitwise and, so we simulate it with a division (shift right) and mod. This only works when
     * [permissionBit] has exactly one bit set, i.e. the Long is a power of two.
     */
    @Query(
        "select pe from PermissionEntity pe " +
            "inner join pe.kayttooikeustasoEntity as kayttooikeustaso " +
            "where pe.userId = :userId " +
            "and mod(kayttooikeustaso.permissionCode / :permissionBit , 2) = 1"
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
    @JoinColumn(name = "kayttooikeustaso_id")
    var kayttooikeustasoEntity: KayttooikeustasoEntity,
) {
    val kayttooikeustaso: Kayttooikeustaso
        get() = kayttooikeustasoEntity.kayttooikeustaso

    fun toDomain() = Permission(id, userId, hankeId, kayttooikeustaso)

    fun hasPermission(permission: PermissionCode): Boolean =
        kayttooikeustasoEntity.hasPermission(permission)
}

@JsonView(ChangeLogView::class)
data class Permission(
    override val id: Int,
    val userId: String,
    val hankeId: Int,
    var kayttooikeustaso: Kayttooikeustaso,
) : HasId<Int>
