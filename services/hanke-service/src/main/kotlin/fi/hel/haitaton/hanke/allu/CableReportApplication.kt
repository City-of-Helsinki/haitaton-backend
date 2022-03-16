package fi.hel.haitaton.hanke.allu

import org.geojson.GeometryCollection
import java.time.ZonedDateTime

class CableReportApplication(
        // Common, required
        val name: String,
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
) {
    // Common, not required
    var postalAddress: PostalAddress? = null
    var representativeWithContacts: CustomerWithContacts? = null
    var invoicingCustomer: Customer? = null
    var customerReference: String? = null
    var area: Double? = null

    // CableReport specific, not required
    var propertyDeveloperWithContacts: CustomerWithContacts? = null // rakennuttaja
    var constructionWork: Boolean = false
    var maintenanceWork: Boolean = false
    var emergencyWork: Boolean = false
    var propertyConnectivity: Boolean = false // tontti-/kiinteistöliitos
}

data class CableReportInformationRequestResponse(
        val applicationData: CableReportApplication,
        val updatedFields: List<InformationRequestFieldKey>
)
