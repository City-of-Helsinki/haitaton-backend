package fi.hel.haitaton.hanke.organisaatio

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import javax.persistence.*

@RestController
@RequestMapping("/organisaatiot")
@Validated
class OrganisaatioController(@Autowired private val service: OrganisaatioService) {

    @GetMapping
    fun getAll(): List<Organisaatio> = service.getAll()

}

class OrganisaatioService(@Autowired val organisaatioRepository: OrganisaatioRepository) {

    fun getAll() = organisaatioRepository.findAllByOrderByNimiAsc()
            .map { Organisaatio(it.id, it.organisaatioTunnus, it.nimi) }

}

data class Organisaatio(val id: Int, val tunnus: String, val nimi: String)

@Entity
@Table(name = "organisaatio")
class OrganisaatioEntity(
        val organisaatioTunnus: String,
        val nimi: String,
        // val createdAt: LocalDateTime,
        // var modifiedAt: LocalDateTime,
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Int = 0,
)

interface OrganisaatioRepository : JpaRepository<OrganisaatioEntity, Int> {
    fun findAllByOrderByNimiAsc(): List<OrganisaatioEntity>
}
