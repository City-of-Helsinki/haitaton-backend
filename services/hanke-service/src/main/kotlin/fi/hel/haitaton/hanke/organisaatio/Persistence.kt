package fi.hel.haitaton.hanke.organisaatio

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import javax.persistence.*

@Entity @Table(name = "organisaatio")
class OrganisaatioEntity(
    var organisaatioTunnus: String? = null,
    var nimi: String? = null,
    var createdAt: LocalDateTime? = null,
    var modifiedAt: LocalDateTime? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
)

interface OrganisaatioRepository : JpaRepository<OrganisaatioEntity, Int> {
    fun findAllByOrderByNimiAsc(): Iterable<OrganisaatioEntity>
}
