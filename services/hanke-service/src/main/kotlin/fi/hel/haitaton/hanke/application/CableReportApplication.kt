package fi.hel.haitaton.hanke.application

import org.locationtech.jts.geom.GeometryCollection
import java.time.ZonedDateTime

class CableReportApplication(
        name: String,
        customerWithContacts: CustomerWithContacts,
        geometry: GeometryCollection,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        pendingOnClient: Boolean,
        identificationNumber: String,
        clientApplicationKind: String,
        workDescription: String,
        contractorWithContacts: CustomerWithContacts, // työn suorittaja
) {
    var representativeWithContacts: CustomerWithContacts? = null
    var invoicingCustomer: Customer? = null
    var customerReference: String? = null
    var propertyDeveloperWithContacts: CustomerWithContacts? = null // rakennuttaja
    var constructionWork: Boolean = false
    var maintenanceWork: Boolean = false
    var emergencyWork: Boolean = false
    var propertyConnectivity: Boolean = false // tontti-/kiinteistöliitos
    val area: Double by lazy { geometry.area }
}
