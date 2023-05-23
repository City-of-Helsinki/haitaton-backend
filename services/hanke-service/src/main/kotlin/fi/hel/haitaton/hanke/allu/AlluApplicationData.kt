package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime
import org.geojson.GeometryCollection

sealed interface AlluApplicationData {
    val name: String
    val pendingOnClient: Boolean
}

data class AlluCableReportApplicationData(
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
) : AlluApplicationData

data class CableReportInformationRequestResponse(
    val applicationData: AlluCableReportApplicationData,
    val updatedFields: List<InformationRequestFieldKey>
)
