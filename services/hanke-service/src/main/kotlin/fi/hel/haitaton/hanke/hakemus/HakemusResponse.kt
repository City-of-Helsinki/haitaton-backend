package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationType
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
    val pendingOnClient: Boolean
    val name: String
    val postalAddress: PostalAddress?
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<ApplicationArea>?
    val customerWithContacts: CustomerWithContactsResponse?
}

data class JohtoselvitysHakemusDataResponse(
    override val applicationType: ApplicationType,
    override val pendingOnClient: Boolean,
    // 1. sivu Perustiedot (first filled in Create)
    /** Työn nimi */
    override val name: String,
    /** Katuosoite */
    override val postalAddress: PostalAddress? = null,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean = false,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean = false,
    /** Työssä on kyse: Kiinteistöliittymien rakentamisesta */
    val propertyConnectivity: Boolean = false, // tontti-/kiinteistöliitos
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean = false,
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
    override val areas: List<ApplicationArea>?,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override val customerWithContacts: CustomerWithContactsResponse? = null,
    /** Työn suorittajan tiedot */
    val contractorWithContacts: CustomerWithContactsResponse? = null,
    /** Rakennuttajan tiedot */
    val propertyDeveloperWithContacts: CustomerWithContactsResponse? = null,
    /** Asianhoitajan tiedot */
    val representativeWithContacts: CustomerWithContactsResponse? = null,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusDataResponse {
    fun customersByRole(): List<Pair<ApplicationContactType, CustomerWithContactsResponse>> =
        listOfNotNull(
            customerWithContacts?.let { ApplicationContactType.HAKIJA to it },
            contractorWithContacts?.let { ApplicationContactType.TYON_SUORITTAJA to it },
            propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
            representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
        )
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
