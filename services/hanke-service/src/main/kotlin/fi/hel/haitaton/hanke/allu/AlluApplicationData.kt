package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import java.time.ZonedDateTime
import org.geojson.GeometryCollection

@JsonInclude(Include.NON_NULL)
sealed interface AlluApplicationData {
    val identificationNumber: String
    val pendingOnClient: Boolean
    val name: String
    val startTime: ZonedDateTime
    val endTime: ZonedDateTime
    val geometry: GeometryCollection
    val area: Double?
    val customerWithContacts: CustomerWithContacts
    val representativeWithContacts: CustomerWithContacts?
    val postalAddress: PostalAddress?
    val invoicingCustomer: Customer?
    val customerReference: String?
    val trafficArrangementImages: List<Int>?
    val clientApplicationKind: String
}

data class AlluCableReportApplicationData(
    override val identificationNumber: String,
    override val pendingOnClient: Boolean,
    override val name: String,
    override val postalAddress: PostalAddress? = null,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val propertyConnectivity: Boolean = false,
    val emergencyWork: Boolean = false,
    val workDescription: String,
    override val clientApplicationKind: String,
    override val startTime: ZonedDateTime,
    override val endTime: ZonedDateTime,
    override val geometry: GeometryCollection,
    override val area: Double? = null,
    override val customerWithContacts: CustomerWithContacts,
    val contractorWithContacts: CustomerWithContacts,
    val propertyDeveloperWithContacts: CustomerWithContacts? = null,
    override val representativeWithContacts: CustomerWithContacts? = null,
    override val invoicingCustomer: Customer? = null,
    override val customerReference: String? = null,
    override val trafficArrangementImages: List<Int>? = null,
) : AlluApplicationData

data class CableReportInformationRequestResponse(
    val applicationData: AlluCableReportApplicationData,
    val updatedFields: List<InformationRequestFieldKey>
)

data class AlluExcavationNotificationApplicationData(
    override val identificationNumber: String,
    override val pendingOnClient: Boolean,
    override val name: String,
    val workPurpose: String,
    override val clientApplicationKind: String,
    val constructionWork: Boolean? = null,
    val maintenanceWork: Boolean? = null,
    val emergencyWork: Boolean? = null,
    val cableReports: List<String>? = null,
    val placementContracts: List<String>? = null,
    override val startTime: ZonedDateTime,
    override val endTime: ZonedDateTime,
    override val geometry: GeometryCollection,
    override val area: Double? = null,
    override val postalAddress: PostalAddress? = null,
    override val customerWithContacts: CustomerWithContacts,
    val contractorWithContacts: CustomerWithContacts,
    val propertyDeveloperWithContacts: CustomerWithContacts? = null,
    override val representativeWithContacts: CustomerWithContacts? = null,
    override val invoicingCustomer: Customer? = null,
    override val customerReference: String? = null,
    val additionalInfo: String? = null,
    val pksCard: Boolean? = null,
    val selfSupervision: Boolean? = null,
    val propertyConnectivity: Boolean? = null,
    override val trafficArrangementImages: List<Int>? = null,
    val trafficArrangements: String? = null,
    val trafficArrangementImpediment: TrafficArrangementImpediment? = null,
) : AlluApplicationData

enum class TrafficArrangementImpediment {
    NO_IMPEDIMENT,
    SIGNIFICANT_IMPEDIMENT,
    IMPEDIMENT_FOR_HEAVY_TRAFFIC,
    INSIGNIFICANT_IMPEDIMENT,
}
