package fi.hel.haitaton.hanke.permissions

import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToOne
import javax.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(name = "hanke_kayttaja")
class HankeKayttajaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "hanke_id") val hankeId: Int,
    val nimi: String,
    val sahkoposti: String,
    @OneToOne
    @JoinColumn(name = "permission_id", updatable = true, nullable = true)
    val permission: PermissionEntity?,
    @OneToOne
    @JoinColumn(name = "tunniste_id", updatable = true, nullable = true)
    val kayttajaTunniste: KayttajaTunnisteEntity?,
)

@Repository
interface HankeKayttajaRepository : JpaRepository<HankeKayttajaEntity, UUID> {
    fun findByHankeIdAndSahkopostiIn(
        hankeId: Int,
        sahkopostit: List<String>
    ): List<HankeKayttajaEntity>
}
