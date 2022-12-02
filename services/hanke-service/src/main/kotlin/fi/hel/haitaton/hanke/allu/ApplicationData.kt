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
    val pendingOnClient: Boolean

    fun copy(pendingOnClient: Boolean): ApplicationData
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
    override val pendingOnClient: Boolean,
    val identificationNumber: String,

    // CableReport specific, required
    val clientApplicationKind: String,
    val workDescription: String,
    val contractorWithContacts: CustomerWithContacts, // työn suorittaja

    // Common, not required
    val postalAddress: PostalAddress? = null,
    val representativeWithContacts: CustomerWithContacts? = null,
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
}

data class CableReportInformationRequestResponse(
    val applicationData: CableReportApplicationData,
    val updatedFields: List<InformationRequestFieldKey>
)
