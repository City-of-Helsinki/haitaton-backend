package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.isNullOrBlank
import java.time.ZonedDateTime
import java.util.UUID

sealed interface HakemusUpdateRequest {
    /** Nimi */
    val name: String
    /** Katuosoite */
    val streetAddress: String
    /** Työn arvioitu alkupäivä */
    val startTime: ZonedDateTime?
    /** Työn arvioitu loppupäivä */
    val endTime: ZonedDateTime?
    /** Työalueet */
    val areas: List<ApplicationArea>?
    /** Hakijan tiedot */
    val customerWithContacts: HakemusUpdateRequestCustomerWithContacts?
    /** Asianhoitajan tiedot */
    val representativeWithContacts: HakemusUpdateRequestCustomerWithContacts?
}

data class JohtoselvityshakemusUpdateRequest(
    // 1. sivu Perustiedot (first filled in Create) //
    /** Nimi */
    override val name: String,
    /** Katuosoite */
    override val streetAddress: String,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean,
    /** Työssä on kyse: Olemassa olevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean,
    /** Työssä on kyse: Kiinteistöliittymien rakentamisesta */
    val propertyConnectivity: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean,
    /** Louhitaanko työn yhteydessä, esimerkiksi kallioperää? */
    val rockExcavation: Boolean,
    /** Työn kuvaus */
    val workDescription: String,
    // 2. sivu Alueet
    /** Työn arvioitu alkupäivä */
    override val startTime: ZonedDateTime? = null,
    /** Työn arvioitu loppupäivä */
    override val endTime: ZonedDateTime? = null,
    /** Työalueet */
    override val areas: List<ApplicationArea>? = null,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override val customerWithContacts: HakemusUpdateRequestCustomerWithContacts? = null,
    /** Työn suorittajan tiedot */
    val contractorWithContacts: HakemusUpdateRequestCustomerWithContacts? = null,
    /** Rakennuttajan tiedot */
    val propertyDeveloperWithContacts: HakemusUpdateRequestCustomerWithContacts? = null,
    /** Asianhoitajan tiedot */
    override val representativeWithContacts: HakemusUpdateRequestCustomerWithContacts? = null,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusUpdateRequest {

    /**
     * Returns true if this application data has changes compared to the given [applicationData].
     */
    fun hasChanges(applicationData: CableReportApplicationData): Boolean =
        name != applicationData.name ||
            streetAddress != applicationData.postalAddress?.streetAddress?.streetName ||
            constructionWork != applicationData.constructionWork ||
            maintenanceWork != applicationData.maintenanceWork ||
            propertyConnectivity != applicationData.propertyConnectivity ||
            emergencyWork != applicationData.emergencyWork ||
            rockExcavation != applicationData.rockExcavation ||
            workDescription != applicationData.workDescription ||
            startTime != applicationData.startTime ||
            endTime != applicationData.endTime ||
            areas != applicationData.areas ||
            customerWithContacts?.hasChanges(applicationData.customerWithContacts)
                ?: !applicationData.customerWithContacts.isNullOrBlank() ||
            contractorWithContacts?.hasChanges(applicationData.contractorWithContacts)
                ?: !applicationData.contractorWithContacts.isNullOrBlank() ||
            propertyDeveloperWithContacts?.hasChanges(applicationData.propertyDeveloperWithContacts)
                ?: !applicationData.propertyDeveloperWithContacts.isNullOrBlank() ||
            representativeWithContacts?.hasChanges(applicationData.representativeWithContacts)
                ?: !applicationData.representativeWithContacts.isNullOrBlank()
}

data class HakemusUpdateRequestCustomerWithContacts(
    val customer: HakemusUpdateRequestCustomer,
    val contacts: List<HakemusUpdateRequestContact>
) {
    /**
     * Returns true if this customer has changes compared to the given [customerWithContacts]. For
     * contacts, we check that both have the same ids (order doesn't matter).
     */
    fun hasChanges(customerWithContacts: CustomerWithContacts?): Boolean =
        customerWithContacts?.let {
            customer.hasChanges(it.customer) ||
                contacts.size != customerWithContacts.contacts.size ||
                contacts.map { contact -> contact.id }.toSet() !=
                    customerWithContacts.contacts.map { contact -> contact.id }.toSet()
        } ?: !customer.isEmpty()
}

data class HakemusUpdateRequestCustomer(
    /** Tyyppi */
    val type: CustomerType? = null,
    /** Nimi */
    val name: String? = null,
    /** Sähköposti */
    val email: String? = null,
    /** Puhelin */
    val phone: String? = null,
    /** Y-tunnus */
    val registryKey: String? = null,
) {

    /** Returns true if this customer has changes compared to the given [customer]. */
    fun hasChanges(customer: Customer): Boolean =
        type != customer.type ||
            name != customer.name ||
            email != customer.email ||
            phone != customer.phone ||
            registryKey != customer.registryKey

    fun isEmpty(): Boolean =
        type == null &&
            name.isNullOrBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            registryKey.isNullOrBlank()
}

data class HakemusUpdateRequestContact(
    /** Hankekayttajan id */
    val id: UUID,
    /** Tilaaja */
    val orderer: Boolean
)
