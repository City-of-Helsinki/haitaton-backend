package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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

    /** An application must belong to a Hanke. Thus, hankeTunnus must be present. */
    fun hankeTunnus(): String = hanke.hankeTunnus
}
