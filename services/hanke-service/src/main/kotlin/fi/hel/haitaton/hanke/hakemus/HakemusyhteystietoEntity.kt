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
    @Id override val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) override var tyyppi: CustomerType,
    @Enumerated(EnumType.STRING) override val rooli: ApplicationContactType,
    override var nimi: String,
    override var sahkoposti: String,
    override var puhelinnumero: String,
    @Column(name = "registry_key") override var registryKey: String?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: HakemusEntity,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hakemusyhteystieto",
        cascade = [CascadeType.ALL],
        orphanRemoval = true)
    @BatchSize(size = 100)
    override val yhteyshenkilot: MutableList<HakemusyhteyshenkiloEntity> = mutableListOf(),
) : YhteystietoEntity<HakemusyhteyshenkiloEntity> {
    fun copyWithHakemus(hakemus: HakemusEntity) =
        HakemusyhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                registryKey = registryKey,
                application = hakemus)
            .also { newEntity ->
                newEntity.yhteyshenkilot.addAll(
                    yhteyshenkilot.map { it.copyWithYhteystieto(newEntity) })
            }
}

@Entity
@Table(name = "hakemusyhteyshenkilo")
class HakemusyhteyshenkiloEntity(
    @Id override val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hakemusyhteystieto_id")
    var hakemusyhteystieto: HakemusyhteystietoEntity,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hankekayttaja_id")
    override var hankekayttaja: HankekayttajaEntity,
    override var tilaaja: Boolean
) : YhteyshenkiloEntity {
    fun copyWithYhteystieto(yhteystieto: HakemusyhteystietoEntity) =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = yhteystieto, hankekayttaja = hankekayttaja, tilaaja = tilaaja)
}

interface HakemusyhteystietoRepository : JpaRepository<HakemusyhteystietoEntity, UUID>

interface HakemusyhteyshenkiloRepository : JpaRepository<HakemusyhteyshenkiloEntity, UUID>
