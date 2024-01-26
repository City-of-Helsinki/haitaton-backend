package fi.hel.haitaton.hanke

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

@Entity
@Table(name = "hankeyhteyshenkilo")
class HankeYhteyshenkiloEntity(
    @JsonView(ChangeLogView::class) @Id val id: UUID = UUID.randomUUID(),
    @JsonView(ChangeLogView::class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hankekayttaja_id")
    var hankeKayttaja: HankekayttajaEntity,
    @JsonView(NotInChangeLogView::class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hankeyhteystieto_id")
    var hankeYhteystieto: HankeYhteystietoEntity,
) {
    fun toDomain(): Yhteyshenkilo =
        Yhteyshenkilo(
            hankeKayttaja.id,
            hankeKayttaja.etunimi,
            hankeKayttaja.sukunimi,
            hankeKayttaja.sahkoposti,
            hankeKayttaja.puhelin,
        )
}

interface HankeYhteyshenkiloRepository : JpaRepository<HankeYhteyshenkiloEntity, UUID> {
    @Query(
        "select new fi.hel.haitaton.hanke.HankeYhteyshenkiloIdentifiers(yh.id, k.id, yt.id) " +
            "from HankeYhteyshenkiloEntity yh " +
            "inner join yh.hankeKayttaja as k " +
            "inner join yh.hankeYhteystieto as yt "
    )
    fun findIds(): List<HankeYhteyshenkiloIdentifiers>
}

data class HankeYhteyshenkiloIdentifiers(
    val id: UUID?,
    val kayttajaId: UUID?,
    val yhteystietoId: Int?
)
