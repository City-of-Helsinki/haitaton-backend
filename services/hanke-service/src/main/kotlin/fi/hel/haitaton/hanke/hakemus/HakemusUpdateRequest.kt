package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import fi.hel.haitaton.hanke.allu.CustomerType
import java.time.ZonedDateTime
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "applicationType",
    visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = JohtoselvityshakemusUpdateRequest::class, name = "CABLE_REPORT"),
    JsonSubTypes.Type(value = KaivuilmoitusUpdateRequest::class, name = "EXCAVATION_NOTIFICATION"))
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed interface HakemusUpdateRequest {
    val applicationType: ApplicationType
    val name: String
    val workDescription: String
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<Hakemusalue>?
    val customerWithContacts: CustomerWithContactsRequest?
    val representativeWithContacts: CustomerWithContactsRequest?

    /**
     * Returns true if this application update request has changes compared to the given
     * [hakemusData].
     */
    fun hasChanges(hakemusData: HakemusData): Boolean

    /**
     * Converts this update request to an [HakemusEntityData] object using the given
     * [hakemusEntityData] as a basis.
     */
    fun toEntityData(hakemusEntityData: HakemusEntityData): HakemusEntityData

    fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsRequest?>
}

data class JohtoselvityshakemusUpdateRequest(
    override val applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
    // 1. sivu Perustiedot (first filled in Create)
    /** Työn nimi */
    override val name: String,
    /** Katuosoite */
    val postalAddress: PostalAddressRequest?,
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
    val rockExcavation: Boolean,
    /** Työn kuvaus */
    override val workDescription: String,
    // 2. sivu Alueet
    /** Työn arvioitu alkupäivä */
    override val startTime: ZonedDateTime? = null,
    /** Työn arvioitu loppupäivä */
    override val endTime: ZonedDateTime? = null,
    /** Työalueet */
    override val areas: List<JohtoselvitysHakemusalue>? = null,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override val customerWithContacts: CustomerWithContactsRequest? = null,
    /** Työn suorittajan tiedot */
    val contractorWithContacts: CustomerWithContactsRequest? = null,
    /** Rakennuttajan tiedot */
    val propertyDeveloperWithContacts: CustomerWithContactsRequest? = null,
    /** Asianhoitajan tiedot */
    override val representativeWithContacts: CustomerWithContactsRequest? = null,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusUpdateRequest {

    override fun hasChanges(hakemusData: HakemusData): Boolean {
        hakemusData as JohtoselvityshakemusData

        return name != hakemusData.name ||
            (postalAddress?.streetAddress?.streetName ?: "") !=
                (hakemusData.postalAddress?.streetAddress?.streetName ?: "") ||
            constructionWork != hakemusData.constructionWork ||
            maintenanceWork != hakemusData.maintenanceWork ||
            propertyConnectivity != hakemusData.propertyConnectivity ||
            emergencyWork != hakemusData.emergencyWork ||
            rockExcavation != hakemusData.rockExcavation ||
            workDescription != hakemusData.workDescription ||
            startTime != hakemusData.startTime ||
            endTime != hakemusData.endTime ||
            areas != hakemusData.areas ||
            customerWithContacts.hasChanges(hakemusData.customerWithContacts) ||
            contractorWithContacts.hasChanges(hakemusData.contractorWithContacts) ||
            propertyDeveloperWithContacts.hasChanges(hakemusData.propertyDeveloperWithContacts) ||
            representativeWithContacts.hasChanges(hakemusData.propertyDeveloperWithContacts)
    }

    override fun toEntityData(hakemusEntityData: HakemusEntityData) =
        (hakemusEntityData as JohtoselvityshakemusEntityData).copy(
            name = this.name,
            postalAddress =
                PostalAddress(StreetAddress(this.postalAddress?.streetAddress?.streetName), "", ""),
            constructionWork = this.constructionWork,
            maintenanceWork = this.maintenanceWork,
            propertyConnectivity = this.propertyConnectivity,
            emergencyWork = this.emergencyWork,
            rockExcavation = this.rockExcavation,
            workDescription = this.workDescription,
            startTime = this.startTime,
            endTime = this.endTime,
            areas = this.areas,
        )

    override fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsRequest?> =
        mapOf(
            ApplicationContactType.HAKIJA to customerWithContacts,
            ApplicationContactType.TYON_SUORITTAJA to contractorWithContacts,
            ApplicationContactType.RAKENNUTTAJA to propertyDeveloperWithContacts,
            ApplicationContactType.ASIANHOITAJA to representativeWithContacts,
        )
}

data class KaivuilmoitusUpdateRequest(
    override val applicationType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
    // 1. sivu Perustiedot (first filled in Create)
    /** Työn nimi */
    override val name: String,
    /** Työn kuvaus */
    override val workDescription: String,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    val constructionWork: Boolean,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    val maintenanceWork: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    val emergencyWork: Boolean,
    /** Hae uusi johtoselvitys? false = kyllä, true = ei */
    val cableReportDone: Boolean,
    /**
     * Uusi johtoselvitys - Louhitaanko työn yhteydessä, esimerkiksi kallioperää? Täytyy olla
     * annettu, jos [cableReportDone] == false
     */
    val rockExcavation: Boolean? = null,
    /** Tehtyjen johtoselvitysten tunnukset */
    val cableReports: List<String>? = emptyList(),
    /** Sijoitussopimukset */
    val placementContracts: List<String>? = emptyList(),
    /** Työhön vaadittava pätevyys */
    val requiredCompetence: Boolean = false,
    // 2. sivu Alueet
    /** Työn arvioitu alkupäivä */
    override val startTime: ZonedDateTime? = null,
    /** Työn arvioitu loppupäivä */
    override val endTime: ZonedDateTime? = null,
    /** Työalueet */
    override val areas: List<KaivuilmoitusAlue>? = null,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override val customerWithContacts: CustomerWithContactsRequest? = null,
    /** Työn suorittajan tiedot */
    val contractorWithContacts: CustomerWithContactsRequest? = null,
    /** Rakennuttajan tiedot */
    val propertyDeveloperWithContacts: CustomerWithContactsRequest? = null,
    /** Asianhoitajan tiedot */
    override val representativeWithContacts: CustomerWithContactsRequest? = null,
    /** Laskutustiedot */
    val invoicingCustomer: InvoicingCustomerRequest? = null,
    // 4. sivu Liitteet
    val additionalInfo: String? = null,
    // 5. sivu Yhteenveto (no input data)
) : HakemusUpdateRequest {

    override fun hasChanges(hakemusData: HakemusData): Boolean {
        hakemusData as KaivuilmoitusData

        val areas = areas?.map { it.withoutTormaystarkastelut() }
        val newAreas = hakemusData.areas?.map { it.withoutTormaystarkastelut() }

        return name != hakemusData.name ||
            workDescription != hakemusData.workDescription ||
            constructionWork != hakemusData.constructionWork ||
            maintenanceWork != hakemusData.maintenanceWork ||
            emergencyWork != hakemusData.emergencyWork ||
            cableReportDone != hakemusData.cableReportDone ||
            rockExcavation != hakemusData.rockExcavation ||
            cableReports != hakemusData.cableReports ||
            placementContracts != hakemusData.placementContracts ||
            requiredCompetence != hakemusData.requiredCompetence ||
            startTime != hakemusData.startTime ||
            endTime != hakemusData.endTime ||
            areas != newAreas ||
            customerWithContacts.hasChanges(hakemusData.customerWithContacts) ||
            contractorWithContacts.hasChanges(hakemusData.contractorWithContacts) ||
            propertyDeveloperWithContacts.hasChanges(hakemusData.propertyDeveloperWithContacts) ||
            representativeWithContacts.hasChanges(hakemusData.representativeWithContacts) ||
            invoicingCustomer.hasChanges(hakemusData.invoicingCustomer) ||
            additionalInfo != hakemusData.additionalInfo
    }

    override fun toEntityData(hakemusEntityData: HakemusEntityData) =
        (hakemusEntityData as KaivuilmoitusEntityData).copy(
            name = this.name,
            workDescription = this.workDescription,
            constructionWork = this.constructionWork,
            maintenanceWork = this.maintenanceWork,
            emergencyWork = this.emergencyWork,
            cableReportDone = this.cableReportDone,
            rockExcavation = this.rockExcavation,
            cableReports = this.cableReports,
            placementContracts = this.placementContracts,
            requiredCompetence = requiredCompetence,
            startTime = this.startTime,
            endTime = this.endTime,
            areas = this.areas,
            invoicingCustomer = this.invoicingCustomer.toCustomer(hakemusEntityData),
            customerReference = this.invoicingCustomer?.customerReference,
            additionalInfo = this.additionalInfo,
        )

    override fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsRequest?> =
        mapOf(
            ApplicationContactType.HAKIJA to customerWithContacts,
            ApplicationContactType.TYON_SUORITTAJA to contractorWithContacts,
            ApplicationContactType.RAKENNUTTAJA to propertyDeveloperWithContacts,
            ApplicationContactType.ASIANHOITAJA to representativeWithContacts,
        )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostalAddressRequest(val streetAddress: StreetAddress)

data class CustomerWithContactsRequest(
    val customer: CustomerRequest,
    val contacts: List<ContactRequest>,
)

/**
 * For updating an existing [Hakemusyhteystieto] (with [yhteystietoId]) or creating a new one
 * (without [yhteystietoId]).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerRequest(
    /** Hakemusyhteystieto id */
    val yhteystietoId: UUID? = null,
    val type: CustomerType,
    val name: String,
    val email: String,
    val phone: String,
    val registryKey: String? = null,
    /** Value is false when read from JSON with null or empty value. */
    val registryKeyHidden: Boolean = false,
) {
    /** Returns true if this customer has changes compared to the given [hakemusyhteystieto]. */
    fun hasChanges(hakemusyhteystieto: Hakemusyhteystieto): Boolean =
        type != hakemusyhteystieto.tyyppi ||
            name != hakemusyhteystieto.nimi ||
            email != hakemusyhteystieto.sahkoposti ||
            phone != hakemusyhteystieto.puhelinnumero ||
            (!registryKeyHidden && registryKey != hakemusyhteystieto.registryKey)
}

/** For referencing [fi.hel.haitaton.hanke.permissions.HankeKayttaja] by its id. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ContactRequest(
    val hankekayttajaId: UUID,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InvoicingCustomerRequest(
    val type: CustomerType,
    val name: String?,
    val registryKey: String?,
    /** Value is false when read from JSON with null or empty value. */
    val registryKeyHidden: Boolean = false,
    val ovt: String?,
    val invoicingOperator: String?,
    val customerReference: String?,
    val postalAddress: InvoicingPostalAddressRequest?,
    val email: String?,
    val phone: String?,
)

data class InvoicingPostalAddressRequest(
    val streetAddress: StreetAddress?,
    val postalCode: String?,
    val city: String?,
)

fun CustomerWithContactsRequest?.hasChanges(hakemusyhteystieto: Hakemusyhteystieto?): Boolean {
    if (this == null) {
        return hakemusyhteystieto != null
    }
    if (hakemusyhteystieto == null) {
        return true
    }
    return customer.hasChanges(hakemusyhteystieto) ||
        contacts.hasChanges(hakemusyhteystieto.yhteyshenkilot)
}

fun List<ContactRequest>?.hasChanges(hakemusyhteyshenkilot: List<Hakemusyhteyshenkilo>): Boolean {
    if (this == null) {
        return hakemusyhteyshenkilot.isNotEmpty()
    }
    val requestIds = this.map { it.hankekayttajaId }.toSet()
    val existingIds = hakemusyhteyshenkilot.map { it.hankekayttajaId }.toSet()
    return requestIds != existingIds
}

fun InvoicingCustomerRequest?.hasChanges(laskutusyhteystieto: Laskutusyhteystieto?): Boolean {
    if (this == null) {
        return laskutusyhteystieto != null
    }
    if (laskutusyhteystieto == null) {
        return true
    }
    return type != laskutusyhteystieto.tyyppi ||
        name != laskutusyhteystieto.nimi ||
        registryKey != laskutusyhteystieto.registryKey ||
        ovt != laskutusyhteystieto.ovttunnus ||
        invoicingOperator != laskutusyhteystieto.valittajanTunnus ||
        customerReference != laskutusyhteystieto.asiakkaanViite ||
        postalAddress.hasChanges(laskutusyhteystieto.postalAddress()) ||
        email != laskutusyhteystieto.sahkoposti ||
        phone != laskutusyhteystieto.puhelinnumero
}

fun InvoicingPostalAddressRequest?.hasChanges(postalAddress: PostalAddress?): Boolean {
    if (this == null) {
        return postalAddress != null
    }
    if (postalAddress == null) {
        return true
    }
    return streetAddress != postalAddress.streetAddress ||
        postalCode != postalAddress.postalCode ||
        city != postalAddress.city
}

fun InvoicingCustomerRequest?.toCustomer(hakemusEntityData: HakemusEntityData): InvoicingCustomer? {
    return this?.let {
        val baseData = (hakemusEntityData as KaivuilmoitusEntityData).invoicingCustomer
        if (baseData != null && type != baseData.type && registryKeyHidden) {
            // If new invoicing customer type doesn't match the old one, the type of registry key
            // will be wrong, but it will be retained if the key is hidden.
            // Validation only checks the new type.
            throw InvalidHiddenRegistryKey(
                "New invoicing customer type doesn't match the old.", type, baseData.type)
        }

        InvoicingCustomer(
            type = it.type,
            name = it.name ?: "",
            postalAddress = it.postalAddress?.combinedAddress(),
            email = it.email,
            phone = it.phone,
            registryKey =
                if (baseData != null && it.registryKeyHidden) baseData.registryKey
                else it.registryKey,
            ovt = it.ovt,
            invoicingOperator = it.invoicingOperator,
        )
    }
}

fun InvoicingPostalAddressRequest?.combinedAddress(): PostalAddress? =
    this?.let {
        PostalAddress(
            streetAddress = StreetAddress(it.streetAddress?.streetName),
            postalCode = it.postalCode ?: "",
            city = it.city ?: "",
        )
    }
