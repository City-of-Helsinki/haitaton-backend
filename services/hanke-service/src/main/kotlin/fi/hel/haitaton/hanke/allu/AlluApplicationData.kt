package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
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

    fun customersByRole(): Map<ApplicationContactType, CustomerWithContacts>
}

data class AlluCableReportApplicationData(
    override val identificationNumber: String,
    override val pendingOnClient: Boolean,
    override val name: String,
    override val postalAddress: PostalAddress?,
    val constructionWork: Boolean,
    val maintenanceWork: Boolean,
    val propertyConnectivity: Boolean,
    val emergencyWork: Boolean,
    val workDescription: String,
    override val clientApplicationKind: String,
    override val startTime: ZonedDateTime,
    override val endTime: ZonedDateTime,
    override val geometry: GeometryCollection,
    override val area: Double?,
    override val customerWithContacts: CustomerWithContacts,
    val contractorWithContacts: CustomerWithContacts,
    val propertyDeveloperWithContacts: CustomerWithContacts?,
    override val representativeWithContacts: CustomerWithContacts?,
    override val invoicingCustomer: Customer?,
    override val customerReference: String?,
    override val trafficArrangementImages: List<Int>?,
) : AlluApplicationData {
    override fun customersByRole(): Map<ApplicationContactType, CustomerWithContacts> =
        listOfNotNull(
                customerWithContacts.let { ApplicationContactType.HAKIJA to it },
                contractorWithContacts.let { ApplicationContactType.TYON_SUORITTAJA to it },
                representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
                propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
            )
            .toMap()
}

data class InformationRequestResponse(
    val applicationData: AlluApplicationData,
    val updatedFields: Set<InformationRequestFieldKey>,
)

data class AlluExcavationNotificationData(
    override val identificationNumber: String,
    override val pendingOnClient: Boolean,
    override val name: String,
    val workPurpose: String,
    override val clientApplicationKind: String,
    val constructionWork: Boolean?,
    val maintenanceWork: Boolean?,
    val emergencyWork: Boolean?,
    val cableReports: List<String>?,
    val placementContracts: List<String>?,
    override val startTime: ZonedDateTime,
    override val endTime: ZonedDateTime,
    override val geometry: GeometryCollection,
    override val area: Double?,
    override val postalAddress: PostalAddress?,
    override val customerWithContacts: CustomerWithContacts,
    val contractorWithContacts: CustomerWithContacts,
    val propertyDeveloperWithContacts: CustomerWithContacts?,
    override val representativeWithContacts: CustomerWithContacts?,
    override val invoicingCustomer: Customer?,
    override val customerReference: String?,
    val additionalInfo: String?,
    val pksCard: Boolean?,
    val selfSupervision: Boolean?,
    val propertyConnectivity: Boolean?,
    override val trafficArrangementImages: List<Int>?,
    val trafficArrangements: String?,
    val trafficArrangementImpediment: TrafficArrangementImpediment?,
) : AlluApplicationData {
    override fun customersByRole(): Map<ApplicationContactType, CustomerWithContacts> =
        listOfNotNull(
                customerWithContacts.let { ApplicationContactType.HAKIJA to it },
                contractorWithContacts.let { ApplicationContactType.TYON_SUORITTAJA to it },
                representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
                propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
            )
            .toMap()
}

enum class TrafficArrangementImpediment {
    NO_IMPEDIMENT,
    SIGNIFICANT_IMPEDIMENT,
    IMPEDIMENT_FOR_HEAVY_TRAFFIC,
    INSIGNIFICANT_IMPEDIMENT,
}
