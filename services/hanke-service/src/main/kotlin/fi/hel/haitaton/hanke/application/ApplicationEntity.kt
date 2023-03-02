package fi.hel.haitaton.hanke.application

import com.vladmihalcea.hibernate.type.json.JsonType
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

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
            hanke.hankeTunnus!!,
        )
}
