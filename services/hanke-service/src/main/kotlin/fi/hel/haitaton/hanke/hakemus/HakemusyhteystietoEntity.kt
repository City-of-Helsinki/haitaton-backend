package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
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

const val DEFAULT_COUNTRY = "FI"

@Entity
@Table(name = "hakemusyhteystieto")
class HakemusyhteystietoEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) val tyyppi: CustomerType,
    @Enumerated(EnumType.STRING) val rooli: ApplicationContactType,
    var nimi: String,
    var sahkoposti: String?,
    var puhelinnumero: String?,
    @Column(name = "y_tunnus") var ytunnus: String?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: ApplicationEntity,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hakemusyhteystieto",
        cascade = [CascadeType.REMOVE],
        orphanRemoval = true
    )
    @BatchSize(size = 100)
    var yhteyshenkilot: List<HakemusyhteyshenkiloEntity> = listOf(),
) {
    fun toCustomerResponse(): CustomerResponse =
        CustomerResponse(
            id,
            tyyppi,
            nimi,
            DEFAULT_COUNTRY,
            sahkoposti,
            puhelinnumero,
            ytunnus,
            null,
            null,
            null
        )

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
                }
        )
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
    fun toContactResponse(): ContactResponse =
        ContactResponse(
            hankekayttaja.id,
            hankekayttaja.etunimi,
            hankekayttaja.sukunimi,
            hankekayttaja.sahkoposti,
            hankekayttaja.puhelin,
            tilaaja
        )
}

interface HakemusyhteystietoRepository : JpaRepository<HakemusyhteystietoEntity, UUID>

interface HakemusyhteyshenkiloRepository : JpaRepository<HakemusyhteyshenkiloEntity, UUID>
