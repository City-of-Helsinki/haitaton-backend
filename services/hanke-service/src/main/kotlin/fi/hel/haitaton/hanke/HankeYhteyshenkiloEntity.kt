package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "hankeyhteyshenkilo")
class HankeYhteyshenkiloEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hankekayttaja_id")
    var hankeKayttaja: HankekayttajaEntity,
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
