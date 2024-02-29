package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import java.time.ZonedDateTime
import java.util.UUID

sealed interface HakemusUpdateRequest {
    var name: String
    var postalAddress: PostalAddressRequest
    var startTime: ZonedDateTime?
    var endTime: ZonedDateTime?
    var areas: List<ApplicationArea>?
    var customerWithContacts: CustomerWithContactsRequest?

    /**
     * Returns true if this application update request has changes compared to the given
     * [applicationEntity].
     */
    fun hasChanges(applicationEntity: ApplicationEntity): Boolean

    /**
     * Converts this update request to an [ApplicationData] object using the given [baseData] as a
     * basis. This means that we take the values in [baseData] and replace only the ones that are
     * defined in this request.
     */
    fun toApplicationData(baseData: ApplicationData): ApplicationData

    fun customersByRole(): Map<ApplicationContactType, CustomerWithContactsRequest?>
}

data class JohtoselvityshakemusUpdateRequest(
    // 1. sivu Perustiedot (first filled in Create)
    /** Työn nimi */
    override var name: String,
    /** Katuosoite */
    override var postalAddress: PostalAddressRequest,
    /** Työssä on kyse: Uuden rakenteen tai johdon rakentamisesta */
    var constructionWork: Boolean,
    /** Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä */
    var maintenanceWork: Boolean,
    /** Työssä on kyse: Kiinteistöliittymien rakentamisesta */
    var propertyConnectivity: Boolean,
    /**
     * Työssä on kyse: Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien
     * vahinkojen välttämiseksi
     */
    var emergencyWork: Boolean,
    /** Louhitaanko työn yhteydessä, esimerkiksi kallioperää? */
    var rockExcavation: Boolean,
    /** Työn kuvaus */
    var workDescription: String,
    // 2. sivu Alueet
    /** Työn arvioitu alkupäivä */
    override var startTime: ZonedDateTime? = null,
    /** Työn arvioitu loppupäivä */
    override var endTime: ZonedDateTime? = null,
    /** Työalueet */
    override var areas: List<ApplicationArea>? = null,
    // 3. sivu Yhteystiedot
    /** Hakijan tiedot */
    override var customerWithContacts: CustomerWithContactsRequest? = null,
    /** Työn suorittajan tiedot */
    var contractorWithContacts: CustomerWithContactsRequest? = null,
    /** Rakennuttajan tiedot */
    var propertyDeveloperWithContacts: CustomerWithContactsRequest? = null,
    /** Asianhoitajan tiedot */
    var representativeWithContacts: CustomerWithContactsRequest? = null,
    // 4. sivu Liitteet (separete endpoint)
    // 5. sivu Yhteenveto (no input data)
) : HakemusUpdateRequest {

    override fun hasChanges(applicationEntity: ApplicationEntity): Boolean {
        val applicationData = applicationEntity.applicationData as CableReportApplicationData
        return name != applicationData.name ||
            (postalAddress.streetAddress.streetName ?: "") !=
                (applicationData.postalAddress?.streetAddress?.streetName ?: "") ||
            constructionWork != applicationData.constructionWork ||
            maintenanceWork != applicationData.maintenanceWork ||
            propertyConnectivity != applicationData.propertyConnectivity ||
            emergencyWork != applicationData.emergencyWork ||
            rockExcavation != applicationData.rockExcavation ||
            workDescription != applicationData.workDescription ||
            startTime != applicationData.startTime ||
            endTime != applicationData.endTime ||
            areas != applicationData.areas ||
            customerWithContacts.hasChanges(
                applicationEntity.yhteystiedot[ApplicationContactType.HAKIJA]
            ) ||
            contractorWithContacts.hasChanges(
                applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]
            ) ||
            propertyDeveloperWithContacts.hasChanges(
                applicationEntity.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]
            ) ||
            representativeWithContacts.hasChanges(
                applicationEntity.yhteystiedot[ApplicationContactType.ASIANHOITAJA]
            )
    }

    override fun toApplicationData(baseData: ApplicationData) =
        (baseData as CableReportApplicationData).copy(
            name = this.name,
            postalAddress =
                PostalAddress(StreetAddress(this.postalAddress.streetAddress.streetName), "", ""),
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

data class PostalAddressRequest(var streetAddress: StreetAddress)

data class CustomerWithContactsRequest(
    var customer: CustomerRequest,
    var contacts: List<ContactRequest>,
)

/**
 * For updating an existing [Hakemusyhteystieto] (with [yhteystietoId]) or creating a new one
 * (without [yhteystietoId]).
 */
data class CustomerRequest(
    /** Hakemusyhteystieto id */
    var yhteystietoId: UUID? = null,
    var type: CustomerType,
    var name: String,
    var email: String,
    var phone: String,
    var registryKey: String? = null,
) {
    /**
     * Returns true if this customer has changes compared to the given [hakemusyhteystietoEntity].
     */
    fun hasChanges(hakemusyhteystietoEntity: HakemusyhteystietoEntity): Boolean =
        type != hakemusyhteystietoEntity.tyyppi ||
            name != hakemusyhteystietoEntity.nimi ||
            email != hakemusyhteystietoEntity.sahkoposti ||
            phone != hakemusyhteystietoEntity.puhelinnumero ||
            registryKey != hakemusyhteystietoEntity.ytunnus
}

/** For referencing [fi.hel.haitaton.hanke.permissions.HankeKayttaja] by its id. */
data class ContactRequest(
    var hankekayttajaId: UUID,
)

fun CustomerWithContactsRequest?.hasChanges(
    hakemusyhteystietoEntity: HakemusyhteystietoEntity?
): Boolean {
    if (this == null) {
        return hakemusyhteystietoEntity != null
    }
    if (hakemusyhteystietoEntity == null) {
        return true
    }
    return this.customer.hasChanges(hakemusyhteystietoEntity) ||
        this.contacts.hasChanges(hakemusyhteystietoEntity.yhteyshenkilot)
}

fun List<ContactRequest>?.hasChanges(
    hakemusyhteyshenkilot: List<HakemusyhteyshenkiloEntity>
): Boolean {
    if (this == null) {
        return hakemusyhteyshenkilot.isNotEmpty()
    }
    val requestIds = this.map { it.hankekayttajaId }.toSet()
    val existingIds = hakemusyhteyshenkilot.map { it.hankekayttaja.id }.toSet()
    return requestIds != existingIds
}
