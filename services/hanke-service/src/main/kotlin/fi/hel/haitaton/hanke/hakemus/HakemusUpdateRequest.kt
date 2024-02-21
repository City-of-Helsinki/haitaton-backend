package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateTrue
import java.time.ZonedDateTime
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "applicationType",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = JohtoselvityshakemusUpdateRequest::class, name = "CABLE_REPORT"),
)
sealed interface HakemusUpdateRequest {
    val applicationType: ApplicationType
    val name: String
    val workDescription: String
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<ApplicationArea>?
    val customerWithContacts: CustomerWithContactsRequest?
    val representativeWithContacts: CustomerWithContactsRequest?

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

    /** Validate draft application. Checks only fields that have some actual data. */
    fun validateForErrors(): ValidationResult =
        validate { notJustWhitespace(name, "name") }
            .and { notJustWhitespace(workDescription, "workDescription") }
            .andWhen(startTime != null && endTime != null) {
                Validators.isBeforeOrEqual(startTime!!, endTime!!, "endTime")
            }
            .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
            .whenNotNull(representativeWithContacts) {
                it.validateForErrors("representativeWithContacts")
            }
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
    override val areas: List<ApplicationArea>? = null,
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

    override fun hasChanges(applicationEntity: ApplicationEntity): Boolean {
        val applicationData = applicationEntity.applicationData as CableReportApplicationData
        return name != applicationData.name ||
            (postalAddress?.streetAddress?.streetName ?: "") !=
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

    override fun validateForErrors(): ValidationResult =
        super.validateForErrors()
            .whenNotNull(postalAddress) { it.validateForErrors("postalAddress") }
            .whenNotNull(contractorWithContacts) { it.validateForErrors("contractorWithContacts") }
            .whenNotNull(propertyDeveloperWithContacts) {
                it.validateForErrors("propertyDeveloperWithContacts")
            }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostalAddressRequest(val streetAddress: StreetAddress) {
    internal fun validateForErrors(path: String) = validate {
        notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName")
    }
}

data class CustomerWithContactsRequest(
    val customer: CustomerRequest,
    val contacts: List<ContactRequest>,
) {
    internal fun validateForErrors(path: String): ValidationResult =
        customer.validateForErrors("$path.customer")
}

/**
 * For updating an existing [Hakemusyhteystieto] (with [yhteystietoId]) or creating a new one
 * (without [yhteystietoId]).
 */
data class CustomerRequest(
    /** Hakemusyhteystieto id */
    val yhteystietoId: UUID? = null,
    val type: CustomerType,
    val name: String,
    val email: String,
    val phone: String,
    val registryKey: String? = null,
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

    internal fun validateForErrors(path: String): ValidationResult =
        validate { notJustWhitespace(name, "$path.name") }
            .and { notJustWhitespace(email, "$path.email") }
            .and { notJustWhitespace(phone, "$path.phone") }
            .andWhen(
                registryKey != null &&
                    (type == CustomerType.COMPANY || type == CustomerType.ASSOCIATION)
            ) {
                validateTrue(registryKey.isValidBusinessId(), "$path.registryKey")
            }
}

/** For referencing [fi.hel.haitaton.hanke.permissions.HankeKayttaja] by its id. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ContactRequest(
    val hankekayttajaId: UUID,
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
