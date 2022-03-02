package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.JsonNode
import com.vladmihalcea.hibernate.type.json.JsonType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import javax.persistence.*

enum class ApplicationType{
    CABLE_REPORT,
}

data class ApplicationDTO(
    val id: Long?,
    val applicationType: ApplicationType,
    val applicationData: JsonNode
)

fun applicationToDto(a: AlluApplication) = ApplicationDTO(a.id, a.applicationType, a.applicationData)

@Entity
@Table(name="applications")
@TypeDef(name = "json", typeClass = JsonType::class)
class AlluApplication(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long?,
    var userId: String?,
    @Enumerated(EnumType.STRING)
    val applicationType: ApplicationType,
    @Type(type = "json")
    @Column(columnDefinition = "jsonb")
    var applicationData: JsonNode,
)