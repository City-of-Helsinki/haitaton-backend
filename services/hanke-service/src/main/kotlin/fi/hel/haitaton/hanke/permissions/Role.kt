package fi.hel.haitaton.hanke.permissions

import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

enum class Role {
    KAIKKI_OIKEUDET,
    KAIKKIEN_MUOKKAUS,
    HANKEMUOKKAUS,
    HAKEMUSASIOINTI,
    KATSELUOIKEUS
}

@Entity
@Table(name = "role")
class RoleEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    @Enumerated(EnumType.STRING) val role: Role,
    val permissionCode: Long,
)

@Repository
interface RoleRepository : JpaRepository<RoleEntity, Int> {
    fun findOneByRole(role: Role): RoleEntity
}
