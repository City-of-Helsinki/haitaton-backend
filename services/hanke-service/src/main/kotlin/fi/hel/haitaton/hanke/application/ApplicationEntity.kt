package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
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
import org.hibernate.annotations.Type

@Entity
@Table(name = "applications")
data class ApplicationEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long?,
    var alluid: Int?,
    @Enumerated(EnumType.STRING) var alluStatus: ApplicationStatus?,
    var applicationIdentifier: String?,
    var userId: String?,
    @Enumerated(EnumType.STRING) val applicationType: ApplicationType,
    @Type(JsonType::class) @Column(columnDefinition = "jsonb") var applicationData: ApplicationData,
    @ManyToOne @JoinColumn(updatable = false, nullable = false) var hanke: HankeEntity,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "application",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    @MapKey(name = "rooli")
    var yhteystiedot: MutableMap<ApplicationContactType, HakemusyhteystietoEntity> = mutableMapOf(),
) {
    fun toApplication() =
        Application(
            id,
            alluid,
            alluStatus,
            applicationIdentifier,
            applicationType,
            applicationData,
            hanke.hankeTunnus,
        )

    fun toHakemus(): Hakemus {
        val yhteystiedot = yhteystiedot.mapValues { it.value.toDomain() }

        return when (applicationData) {
            is CableReportApplicationData -> {
                val applicationData =
                    (this.applicationData as CableReportApplicationData).toHakemusData(yhteystiedot)
                Hakemus(
                    id = id!!,
                    alluid = alluid,
                    alluStatus = alluStatus,
                    applicationIdentifier = applicationIdentifier,
                    applicationType = applicationType,
                    applicationData = applicationData,
                    hankeTunnus = hanke.hankeTunnus,
                    hankeId = hanke.id,
                )
            }
            is ExcavationNotificationApplicationData ->
                TODO("Excavation notification not implemented")
        }
    }
}
