package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
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
    val applicationData: HakemusDataResponse,
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
    var customerWithContacts: CustomerWithContactsResponse? = null, // hakija
    override val areas: List<ApplicationArea>?,
    val startTime: ZonedDateTime?,
    val endTime: ZonedDateTime?,
    override val pendingOnClient: Boolean,

    // CableReport specific, required
    val workDescription: String,
    var contractorWithContacts: CustomerWithContactsResponse? = null, // työn suorittaja
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
) : HakemusDataResponse {
    fun customersByRole(): List<Pair<ApplicationContactType, CustomerWithContactsResponse>> =
        listOfNotNull(
            customerWithContacts?.let { ApplicationContactType.HAKIJA to it },
            contractorWithContacts?.let { ApplicationContactType.TYON_SUORITTAJA to it },
            representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
            propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
        )
}

data class CustomerWithContactsResponse(
    val customer: CustomerResponse,
    val contacts: List<ContactResponse>,
)

data class CustomerResponse(
    val yhteystietoId: UUID? = null,
    val type: CustomerType?, // Mandatory in Allu, but not in drafts.
    val name: String,
    val country: String, // ISO 3166-1 alpha-2 country code
    val email: String?,
    val phone: String?,
    val registryKey: String?, // y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val sapCustomerNumber: String?, // customer's sap number
) {
    /**
     * Check if this customer contains any actual personal information.
     *
     * Country alone isn't considered personal information when it's dissociated from other
     * information, so it's not checked here.
     */
    fun hasPersonalInformation() =
        !(name.isBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            registryKey.isNullOrBlank() &&
            ovt.isNullOrBlank() &&
            invoicingOperator.isNullOrBlank() &&
            sapCustomerNumber.isNullOrBlank())
}

data class ContactResponse(
    val hankekayttajaId: UUID,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phone: String?,
    val orderer: Boolean = false,
) {
    /** Check if this contact is blank, i.e. it doesn't contain any actual contact information. */
    @JsonIgnore fun isBlank() = listOf(firstName, lastName, email, phone).all { it.isNullOrBlank() }

    fun hasInformation() = !isBlank()
}
