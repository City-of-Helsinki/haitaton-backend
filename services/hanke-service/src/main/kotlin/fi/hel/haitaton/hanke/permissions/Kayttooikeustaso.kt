package fi.hel.haitaton.hanke.permissions

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

enum class Kayttooikeustaso {
    KAIKKI_OIKEUDET,
    KAIKKIEN_MUOKKAUS,
    HANKEMUOKKAUS,
    HAKEMUSASIOINTI,
    KATSELUOIKEUS
}

@Entity
@Table(name = "kayttooikeustaso")
class KayttooikeustasoEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    @Enumerated(EnumType.STRING) val kayttooikeustaso: Kayttooikeustaso,
    val permissionCode: Long,
)

@Repository
interface KayttooikeustasoRepository : JpaRepository<KayttooikeustasoEntity, Int> {
    fun findOneByKayttooikeustaso(kayttooikeustaso: Kayttooikeustaso): KayttooikeustasoEntity
}
