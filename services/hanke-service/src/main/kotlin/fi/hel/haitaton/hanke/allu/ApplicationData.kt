package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
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
}

@JsonView(ChangeLogView::class)
data class CableReportApplicationData(
    @JsonView(NotInChangeLogView::class) override val applicationType: ApplicationType,

    // Common, required
    override val name: String,
    val customerWithContacts: CustomerWithContacts,
    val geometry: GeometryCollection,
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    var pendingOnClient: Boolean,
    val identificationNumber: String,

    // CableReport specific, required
    val clientApplicationKind: String,
    val workDescription: String,
    val contractorWithContacts: CustomerWithContacts, // työn suorittaja

    // Common, not required
    var postalAddress: PostalAddress? = null,
    var representativeWithContacts: CustomerWithContacts? = null,
    var invoicingCustomer: Customer? = null,
    var customerReference: String? = null,
    var area: Double? = null,

    // CableReport specific, not required
    var propertyDeveloperWithContacts: CustomerWithContacts? = null, // rakennuttaja
    var constructionWork: Boolean = false,
    var maintenanceWork: Boolean = false,
    var emergencyWork: Boolean = false,
    var propertyConnectivity: Boolean = false, // tontti-/kiinteistöliitos
) : ApplicationData

data class CableReportInformationRequestResponse(
    val applicationData: CableReportApplicationData,
    val updatedFields: List<InformationRequestFieldKey>
)
