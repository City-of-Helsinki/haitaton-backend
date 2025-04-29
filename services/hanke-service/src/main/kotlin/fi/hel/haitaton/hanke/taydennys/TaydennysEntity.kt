package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.domain.HasYhteystietoEntities
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusEntityData
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKey
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.Type

@Entity
@Table(name = "taydennys")
class TaydennysEntity(
    @Id override var id: UUID = UUID.randomUUID(),
    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "taydennyspyynto_id", nullable = false)
    var taydennyspyynto: TaydennyspyyntoEntity,
    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", name = "application_data")
    var hakemusData: HakemusEntityData,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "taydennys",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @MapKey(name = "rooli")
    @BatchSize(size = 100)
    override var yhteystiedot: MutableMap<ApplicationContactType, TaydennysyhteystietoEntity> =
        mutableMapOf(),
) : TaydennysIdentifier, HasYhteystietoEntities<TaydennysyhteyshenkiloEntity> {

    fun toDomain(): Taydennys {
        val yhteystiedot: Map<ApplicationContactType, Hakemusyhteystieto> =
            yhteystiedot.mapValues { it.value.toDomain() }
        val applicationData =
            when (hakemusData) {
                is JohtoselvityshakemusEntityData ->
                    (this.hakemusData as JohtoselvityshakemusEntityData).toHakemusData(yhteystiedot)
                is KaivuilmoitusEntityData ->
                    (this.hakemusData as KaivuilmoitusEntityData).toHakemusData(yhteystiedot)
            }
        return Taydennys(
            id = id,
            taydennyspyyntoId = taydennyspyynto.id,
            hakemusId = taydennyspyynto.applicationId,
            hakemusData = applicationData,
        )
    }

    override fun taydennyspyyntoId(): UUID = taydennyspyynto.id

    override fun taydennyspyyntoAlluId(): Int = taydennyspyynto.alluId

    override fun hakemusId(): Long = taydennyspyynto.applicationId

    override fun hakemustyyppi(): ApplicationType = hakemusData.applicationType
}
