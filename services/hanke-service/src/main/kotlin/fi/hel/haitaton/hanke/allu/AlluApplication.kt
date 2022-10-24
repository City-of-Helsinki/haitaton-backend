package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.databind.JsonNode
import com.vladmihalcea.hibernate.type.json.JsonType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef

enum class ApplicationType {
    CABLE_REPORT,
}

data class ApplicationDto(
    val id: Long?,
    val alluid: Int?,
    val applicationType: ApplicationType,
    val applicationData: JsonNode
)

fun applicationToDto(a: AlluApplication) =
    ApplicationDto(a.id, a.alluid, a.applicationType, a.applicationData)

@Entity
@Table(name = "applications")
@TypeDef(name = "json", typeClass = JsonType::class)
class AlluApplication(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long?,
    var alluid: Int?,
    var userId: String?,
    @Enumerated(EnumType.STRING) val applicationType: ApplicationType,
    @Type(type = "json") @Column(columnDefinition = "jsonb") var applicationData: JsonNode,
)
