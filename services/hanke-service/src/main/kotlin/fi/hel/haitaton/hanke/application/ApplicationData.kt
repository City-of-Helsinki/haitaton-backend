package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import java.time.ZonedDateTime
import org.geojson.GeometryCollection

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "applicationType",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CableReportApplicationData::class, name = "CABLE_REPORT"),
)
sealed interface ApplicationData {
    val applicationType: ApplicationType
    val name: String
    val pendingOnClient: Boolean
    val geometry: GeometryCollection?
    val areas: List<ApplicationArea>?

    fun copy(pendingOnClient: Boolean): ApplicationData
    fun toAlluData(): AlluApplicationData?
}

@JsonView(ChangeLogView::class)
data class CableReportApplicationData(
    @JsonView(NotInChangeLogView::class) override val applicationType: ApplicationType,

    // Common, required
    override val name: String,
    val customerWithContacts: CustomerWithContacts,
    override val geometry: GeometryCollection?,
    override val areas: List<ApplicationArea>?,
    val startTime: ZonedDateTime?,
    val endTime: ZonedDateTime?,
    override val pendingOnClient: Boolean,
    val identificationNumber: String,

    // CableReport specific, required
    val clientApplicationKind: String,
    val workDescription: String,
    val contractorWithContacts: CustomerWithContacts, // työn suorittaja
    val rockExcavation: Boolean?,

    // Common, not required
    val postalAddress: PostalAddress? = null,
    val representativeWithContacts: CustomerWithContacts? = null, // Asianhoitaja
    val invoicingCustomer: Customer? = null,
    val customerReference: String? = null,
    val area: Double? = null,

    // CableReport specific, not required
    val propertyDeveloperWithContacts: CustomerWithContacts? = null, // rakennuttaja
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val emergencyWork: Boolean = false,
    val propertyConnectivity: Boolean = false, // tontti-/kiinteistöliitos
) : ApplicationData {
    override fun copy(pendingOnClient: Boolean): CableReportApplicationData =
        copy(applicationType = applicationType, pendingOnClient = pendingOnClient)

    override fun toAlluData(): AlluCableReportApplicationData =
        ApplicationDataMapper.toAlluData(this)
}

class AlluDataException(path: String, error: AlluDataError) :
    RuntimeException("Application data failed validation at $path: $error")

enum class AlluDataError(private val errorDescription: String) {
    NULL("Can't be null"),
    EMPTY_OR_NULL("Can't be empty or null"),
    ;

    override fun toString(): String = errorDescription
}
