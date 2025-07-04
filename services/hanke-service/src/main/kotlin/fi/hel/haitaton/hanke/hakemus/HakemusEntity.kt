package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.HasYhteystietoEntities
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusEntity
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKey
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Type

@Entity
@Table(name = "applications")
class HakemusEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override val id: Long,
    override var alluid: Int?,
    @Enumerated(EnumType.STRING) override var alluStatus: ApplicationStatus?,
    override var applicationIdentifier: String?,
    var userId: String?,
    @Enumerated(EnumType.STRING) override val applicationType: ApplicationType,
    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", name = "applicationdata")
    var hakemusEntityData: HakemusEntityData,
    @ManyToOne @JoinColumn(updatable = false, nullable = false) var hanke: HankeEntity,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "application",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @MapKey(name = "rooli")
    @BatchSize(size = 100)
    override var yhteystiedot: MutableMap<ApplicationContactType, HakemusyhteystietoEntity> =
        mutableMapOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hakemus",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @BatchSize(size = 100)
    val valmistumisilmoitukset: MutableList<ValmistumisilmoitusEntity> = mutableListOf(),
) : HakemusIdentifier, HasAlluStatus, HasYhteystietoEntities<HakemusyhteyshenkiloEntity> {
    fun toMetadata(): HakemusMetaData =
        HakemusMetaData(
            id = id,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            hankeTunnus = hanke.hankeTunnus,
        )

    fun toHakemus(): Hakemus {
        val yhteystiedot = yhteystiedot.mapValues { it.value.toDomain() }
        val applicationData =
            when (hakemusEntityData) {
                is JohtoselvityshakemusEntityData ->
                    (this.hakemusEntityData as JohtoselvityshakemusEntityData).toHakemusData(
                        yhteystiedot
                    )
                is KaivuilmoitusEntityData ->
                    (this.hakemusEntityData as KaivuilmoitusEntityData).toHakemusData(yhteystiedot)
            }
        return Hakemus(
            id = id,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            applicationData = applicationData,
            hankeTunnus = hanke.hankeTunnus,
            hankeId = hanke.id,
            valmistumisilmoitukset =
                valmistumisilmoitukset.map { it.toDomain() }.groupBy { it.type },
        )
    }
}
