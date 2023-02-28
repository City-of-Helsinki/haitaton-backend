package fi.hel.haitaton.hanke.application

import com.vladmihalcea.hibernate.type.json.JsonType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import javax.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef

@Entity
@Table(name = "applications")
@TypeDef(name = "json", typeClass = JsonType::class)
data class ApplicationEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long?,
    var alluid: Int?,
    @Enumerated(EnumType.STRING) var alluStatus: ApplicationStatus?,
    var applicationIdentifier: String?,
    var userId: String?,
    @Enumerated(EnumType.STRING) val applicationType: ApplicationType,
    @Type(type = "json") @Column(columnDefinition = "jsonb") var applicationData: ApplicationData,
    @ManyToOne @JoinColumn(updatable = false, nullable = false) var hanke: HankeEntity,
) {
    fun toApplication() =
        Application(
            id,
            alluid,
            alluStatus,
            applicationIdentifier,
            applicationType,
            applicationData,
            hanke.hankeTunnus ?: throw HankeNotFoundException(""),
        )
}
