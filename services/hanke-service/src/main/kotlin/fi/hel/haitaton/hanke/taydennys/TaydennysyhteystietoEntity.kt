package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.YhteystietoEntity
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
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "taydennysyhteystieto")
class TaydennysyhteystietoEntity(
    @Id override val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) override var tyyppi: CustomerType,
    @Enumerated(EnumType.STRING) override val rooli: ApplicationContactType,
    override var nimi: String,
    override var sahkoposti: String,
    override var puhelinnumero: String,
    @Column(name = "registry_key") override var registryKey: String?,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "taydennys_id", nullable = false)
    var taydennys: TaydennysEntity,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "taydennysyhteystieto",
        cascade = [CascadeType.ALL],
        orphanRemoval = true)
    @BatchSize(size = 100)
    override var yhteyshenkilot: MutableList<TaydennysyhteyshenkiloEntity> = mutableListOf(),
) : YhteystietoEntity<TaydennysyhteyshenkiloEntity>
