package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime
import org.geojson.GeometryCollection

sealed interface AlluApplicationData {
    val postalAddress: PostalAddress?
    val name: String
    val customerWithContacts: CustomerWithContacts
    val representativeWithContacts: CustomerWithContacts?
    val invoicingCustomer: Customer?
    val geometry: GeometryCollection
    val startTime: ZonedDateTime
    val endTime: ZonedDateTime
    val pendingOnClient: Boolean
    val identificationNumber: String
    val customerReference: String?
    val area: Double?
    val trafficArrangementImages: List<Int>?
    val clientApplicationKind: String
}

data class AlluCableReportApplicationData(
    override val postalAddress: PostalAddress? = null,
    override val name: String,
    override val customerWithContacts: CustomerWithContacts,
    override val representativeWithContacts: CustomerWithContacts? = null,
    override val invoicingCustomer: Customer? = null,
    override val geometry: GeometryCollection,
    override val startTime: ZonedDateTime,
    override val endTime: ZonedDateTime,
    override val pendingOnClient: Boolean,
    override val identificationNumber: String,
    override val customerReference: String? = null,
    override val area: Double? = null,
    override val trafficArrangementImages: List<Int>? = null,
    override val clientApplicationKind: String,
    val workDescription: String,
    val propertyDeveloperWithContacts: CustomerWithContacts? = null,
    val contractorWithContacts: CustomerWithContacts,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val emergencyWork: Boolean = false,
    val propertyConnectivity: Boolean = false,
) : AlluApplicationData

data class CableReportInformationRequestResponse(
    val applicationData: AlluCableReportApplicationData,
    val updatedFields: List<InformationRequestFieldKey>
)

data class AlluExcavationNotificationApplicationData(
    override val postalAddress: PostalAddress? = null,
    override val name: String,
    override val customerWithContacts: CustomerWithContacts,
    override val representativeWithContacts: CustomerWithContacts? = null,
    override val invoicingCustomer: Customer? = null,
    override val geometry: GeometryCollection,
    override val startTime: ZonedDateTime,
    override val endTime: ZonedDateTime,
    override val pendingOnClient: Boolean,
    override val identificationNumber: String,
    override val customerReference: String? = null,
    override val area: Double? = null,
    override val trafficArrangementImages: List<Int>? = null,
    override val clientApplicationKind: String,
    val contractorWithContacts: CustomerWithContacts,
    val propertyDeveloperWithContacts: CustomerWithContacts? = null,
    val pksCard: Boolean? = null,
    val constructionWork: Boolean? = null,
    val maintenanceWork: Boolean? = null,
    val emergencyWork: Boolean? = null,
    val propertyConnectivity: Boolean? = null,
    val selfSupervision: Boolean? = null,
    val workPurpose: String,
    val additionalInfo: String? = null,
    val trafficArrangements: String? = null,
    val trafficArrangementImpediment: TrafficArrangementImpediment? = null,
    val placementContracts: List<String>? = null,
    val cableReports: List<String>? = null,
) : AlluApplicationData

enum class TrafficArrangementImpediment {
    NO_IMPEDIMENT,
    SIGNIFICANT_IMPEDIMENT,
    IMPEDIMENT_FOR_HEAVY_TRAFFIC,
    INSIGNIFICANT_IMPEDIMENT,
}
