package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
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

@Entity
@Table(name = "hakemusyhteystieto")
class HakemusyhteystietoEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) val tyyppi: CustomerType,
    @Enumerated(EnumType.STRING) val rooli: ApplicationContactType,
    var nimi: String,
    var sahkoposti: String?,
    var puhelinnumero: String?,
    var ytunnus: String?,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: ApplicationEntity,
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "hakemusyhteystietoEntity")
    @BatchSize(size = 100)
    var yhteystiedot: List<HakemusyhteyshenkiloEntity>,
)

@Entity
@Table(name = "hakemusyhteyshenkilo")
class HakemusyhteyshenkiloEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hakemusyhteystieto_id")
    var hakemusyhteystietoEntity: HakemusyhteystietoEntity,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hankekayttaja_id")
    var hankekayttajaEntity: HankekayttajaEntity,
)
