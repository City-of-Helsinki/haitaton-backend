package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.PostalAddress
import java.time.ZonedDateTime
import java.util.UUID

data class HakemusResponse(
    val id: Long,
    val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    var applicationData: HakemusDataResponse,
    val hankeTunnus: String,
)

sealed interface HakemusDataResponse {
    val applicationType: ApplicationType
    val name: String
    val pendingOnClient: Boolean
    val areas: List<ApplicationArea>?
}

data class JohtoselvitysHakemusDataResponse(
    override val applicationType: ApplicationType,

    // Common, required
    override val name: String,
    var customerWithContacts: CustomerWithContactsResponse, // hakija
    override val areas: List<ApplicationArea>?,
    val startTime: ZonedDateTime?,
    val endTime: ZonedDateTime?,
    override val pendingOnClient: Boolean,

    // CableReport specific, required
    val workDescription: String,
    var contractorWithContacts: CustomerWithContactsResponse, // työn suorittaja
    val rockExcavation: Boolean?,

    // Common, not required
    val postalAddress: PostalAddress? = null,
    var representativeWithContacts: CustomerWithContactsResponse? = null, // asianhoitaja
    val invoicingCustomer: Customer? = null,
    val customerReference: String? = null,
    val area: Double? = null,

    // CableReport specific, not required
    var propertyDeveloperWithContacts: CustomerWithContactsResponse? = null, // rakennuttaja
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val emergencyWork: Boolean = false,
    val propertyConnectivity: Boolean = false, // tontti-/kiinteistöliitos
) : HakemusDataResponse

data class CustomerWithContactsResponse(
    val customer: CustomerResponse,
    val contacts: List<ContactResponse>,
)

data class CustomerResponse(
    val yhteystietoId: UUID? = null,
    val type: CustomerType?, // Mandatory in Allu, but not in drafts.
    val name: String?,
    val country: String?, // ISO 3166-1 alpha-2 country code
    val email: String?,
    val phone: String?,
    val registryKey: String?, // y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val sapCustomerNumber: String?, // customer's sap number
)

data class ContactResponse(
    val hankekayttajaId: UUID,
    val tilaaja: Boolean,
)
