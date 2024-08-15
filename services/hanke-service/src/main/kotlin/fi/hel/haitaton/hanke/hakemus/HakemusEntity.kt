package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.ilmoitus.IlmoitusEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
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
data class HakemusEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override val id: Long,
    override var alluid: Int?,
    @Enumerated(EnumType.STRING) var alluStatus: ApplicationStatus?,
    override var applicationIdentifier: String?,
    var userId: String?,
    @Enumerated(EnumType.STRING) val applicationType: ApplicationType,
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
    var yhteystiedot: MutableMap<ApplicationContactType, HakemusyhteystietoEntity> = mutableMapOf(),
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "hakemus",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @BatchSize(size = 100)
    val ilmoitukset: MutableList<IlmoitusEntity> = mutableListOf(),
) : HakemusIdentifier {
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
                        yhteystiedot)
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
            ilmoitukset = ilmoitukset.map { it.toDomain() }.groupBy { it.type },
        )
    }

    /** Returns all distinct contact users for this application. */
    fun allContactUsers(): List<HankekayttajaEntity> =
        yhteystiedot.values
            .flatMap { it.yhteyshenkilot }
            .map { it.hankekayttaja }
            .distinctBy { it.id }
}
