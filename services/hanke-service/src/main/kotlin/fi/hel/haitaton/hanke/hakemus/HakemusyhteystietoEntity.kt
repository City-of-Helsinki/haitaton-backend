package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.UUID
import org.hibernate.annotations.BatchSize
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "hakemusyhteystieto")
class HakemusyhteystietoEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) var tyyppi: CustomerType,
    @Enumerated(EnumType.STRING) val rooli: ApplicationContactType,
    var nimi: String,
    var sahkoposti: String,
    var puhelinnumero: String,
    @Column(name = "y_tunnus") var ytunnus: String?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: HakemusEntity,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hakemusyhteystieto",
        cascade = [CascadeType.ALL],
        orphanRemoval = true)
    @BatchSize(size = 100)
    val yhteyshenkilot: MutableList<HakemusyhteyshenkiloEntity> = mutableListOf(),
) {

    fun toDomain() =
        Hakemusyhteystieto(
            id = id,
            tyyppi = tyyppi,
            rooli = rooli,
            nimi = nimi,
            sahkoposti = sahkoposti,
            puhelinnumero = puhelinnumero,
            ytunnus = ytunnus,
            yhteyshenkilot =
                yhteyshenkilot.map { yhteyshenkilo ->
                    Hakemusyhteyshenkilo(
                        id = id,
                        hankekayttajaId = yhteyshenkilo.hankekayttaja.id,
                        etunimi = yhteyshenkilo.hankekayttaja.etunimi,
                        sukunimi = yhteyshenkilo.hankekayttaja.sukunimi,
                        sahkoposti = yhteyshenkilo.hankekayttaja.sahkoposti,
                        puhelin = yhteyshenkilo.hankekayttaja.puhelin,
                        tilaaja = yhteyshenkilo.tilaaja,
                    )
                })

    fun copyWithHakemus(hakemus: HakemusEntity) =
        HakemusyhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                ytunnus = ytunnus,
                application = hakemus)
            .also { newEntity ->
                newEntity.yhteyshenkilot.addAll(
                    yhteyshenkilot.map { it.copyWithYhteystieto(newEntity) })
            }
}

@Entity
@Table(name = "hakemusyhteyshenkilo")
class HakemusyhteyshenkiloEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hakemusyhteystieto_id")
    var hakemusyhteystieto: HakemusyhteystietoEntity,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hankekayttaja_id")
    var hankekayttaja: HankekayttajaEntity,
    var tilaaja: Boolean
) {
    fun copyWithYhteystieto(yhteystieto: HakemusyhteystietoEntity) =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = yhteystieto, hankekayttaja = hankekayttaja, tilaaja = tilaaja)
}

interface HakemusyhteystietoRepository : JpaRepository<HakemusyhteystietoEntity, UUID>

interface HakemusyhteyshenkiloRepository : JpaRepository<HakemusyhteyshenkiloEntity, UUID>
