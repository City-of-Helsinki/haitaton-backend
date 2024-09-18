package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusResponse
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val valmistumisilmoitukset: Map<ValmistumisilmoitusType, List<ValmistumisilmoitusResponse>>?,
)

sealed interface HakemusDataResponse {
    val applicationType: ApplicationType
    val pendingOnClient: Boolean
    val name: String
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<Hakemusalue>
    val customerWithContacts: CustomerWithContactsResponse?

    fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsResponse>
}

data class JohtoselvitysHakemusDataResponse(
    override val applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
    override val pendingOnClient: Boolean,
    // 1. sivu Perustiedot (first filled in Create)
    /** Työn nimi */
    override val name: String,
    /** Katuosoite */
    val postalAddress: PostalAddress?,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean,
    /** Työssä on kyse: Kiinteistöliittymien rakentamisesta */
    val propertyConnectivity: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean,
    /** Louhitaanko työn yhteydessä, esimerkiksi kallioperää? */
    val rockExcavation: Boolean?,
    /** Työn kuvaus */
    val workDescription: String,
    // 2. sivu Alueet
    /** Työn arvioitu alkupäivä */
    override val startTime: ZonedDateTime?,
    /** Työn arvioitu loppupäivä */
    override val endTime: ZonedDateTime?,
    /** Työalueet */
    override val areas: List<JohtoselvitysHakemusalue>,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override val customerWithContacts: CustomerWithContactsResponse?,
    /** Työn suorittajan tiedot */
    val contractorWithContacts: CustomerWithContactsResponse?,
    /** Rakennuttajan tiedot */
    val propertyDeveloperWithContacts: CustomerWithContactsResponse?,
    /** Asianhoitajan tiedot */
    val representativeWithContacts: CustomerWithContactsResponse?,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusDataResponse {
    override fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsResponse> =
        listOfNotNull(
                customerWithContacts?.let { ApplicationContactType.HAKIJA to it },
                contractorWithContacts?.let { ApplicationContactType.TYON_SUORITTAJA to it },
                propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
                representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
            )
            .toMap()
}

data class KaivuilmoitusDataResponse(
    override val applicationType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
    override val pendingOnClient: Boolean,
    // 1. sivu Perustiedot (first filled in Create)
    /** Työn nimi */
    override val name: String,
    /** Työn kuvaus */
    val workDescription: String,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen kaivuilmoituksen tekemistä merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean,
    /** Hae uusi johtoselvitys? (false means yes) */
    val cableReportDone: Boolean,
    /** Uusi johtoselvitys: Louhitaanko työn yhteydessä, esimerkiksi kallioperää? */
    val rockExcavation: Boolean?,
    /** Tehtyjen johtoselvitysten tunnukset */
    val cableReports: List<String>?,
    /** Sijoitussopimukset */
    val placementContracts: List<String>?,
    /** Työhän vaadittava pätevyys */
    val requiredCompetence: Boolean,
    // 2. sivu Alueet
    /** Työn alkupäivämäärä */
    override val startTime: ZonedDateTime?,
    /** Työn loppupäivämäärä */
    override val endTime: ZonedDateTime?,
    /** Työalueet */
    override val areas: List<KaivuilmoitusAlue>,
    // 3. sivu Haittojen hallinta - included in areas
    // 4. sivu Yhteystiedot
    /** Hakijan tiedot */
    override val customerWithContacts: CustomerWithContactsResponse?,
    /** Työn suorittajan tiedot */
    val contractorWithContacts: CustomerWithContactsResponse?,
    /** Rakennuttajan tiedot */
    val propertyDeveloperWithContacts: CustomerWithContactsResponse?,
    /** Asianhoitajan tiedot */
    val representativeWithContacts: CustomerWithContactsResponse?,
    /** Laskutustiedot */
    val invoicingCustomer: InvoicingCustomerResponse?,
    // 5. sivu Liitteet
    val additionalInfo: String?,
    // 6. sivu Yhteenveto (no input data)
) : HakemusDataResponse {
    override fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsResponse> =
        listOfNotNull(
                customerWithContacts?.let { ApplicationContactType.HAKIJA to it },
                contractorWithContacts?.let { ApplicationContactType.TYON_SUORITTAJA to it },
                propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
                representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
            )
            .toMap()
}

data class CustomerWithContactsResponse(
    val customer: CustomerResponse,
    val contacts: List<ContactResponse>,
)

data class CustomerResponse(
    val yhteystietoId: UUID,
    val type: CustomerType,
    val name: String,
    val email: String,
    val phone: String,
    val registryKey: String?,
    val registryKeyHidden: Boolean,
) {
    /** Check if this customer contains any actual personal information. */
    fun hasPersonalInformation() =
        !(name.isBlank() && email.isBlank() && phone.isBlank() && registryKey.isNullOrBlank())
}

data class ContactResponse(
    val hankekayttajaId: UUID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val orderer: Boolean = false,
) {
    /** Check if this contact is blank, i.e. it doesn't contain any actual contact information. */
    @JsonIgnore fun isBlank() = listOf(firstName, lastName, email, phone).all { it.isBlank() }

    fun hasInformation() = !isBlank()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InvoicingCustomerResponse(
    val type: CustomerType?,
    val name: String?,
    val registryKey: String?,
    val registryKeyHidden: Boolean,
    val ovt: String?,
    val invoicingOperator: String?,
    val customerReference: String?,
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
) {
    /** Check if this customer contains any actual personal information. */
    fun hasPersonalInformation() =
        !(name.isNullOrBlank() &&
            postalAddress.isNullOrBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            registryKey.isNullOrBlank() &&
            ovt.isNullOrBlank() &&
            invoicingOperator.isNullOrBlank() &&
            customerReference.isNullOrBlank())
}
