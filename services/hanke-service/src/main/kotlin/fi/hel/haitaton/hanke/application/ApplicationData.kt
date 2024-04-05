package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.hakemus.Laskutusyhteystieto
import java.time.ZonedDateTime

enum class ApplicationContactType {
    HAKIJA,
    TYON_SUORITTAJA,
    RAKENNUTTAJA,
    ASIANHOITAJA,
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "applicationType",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CableReportApplicationData::class, name = "CABLE_REPORT"),
    JsonSubTypes.Type(value = ExcavationNotificationData::class, name = "EXCAVATION_NOTIFICATION"),
)
sealed interface ApplicationData {
    val applicationType: ApplicationType
    val pendingOnClient: Boolean
    val name: String
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<ApplicationArea>?
    val customerWithContacts: CustomerWithContacts?
    val representativeWithContacts: CustomerWithContacts?

    fun copy(pendingOnClient: Boolean): ApplicationData

    fun toAlluData(hankeTunnus: String): AlluApplicationData

    fun customersWithContacts(): List<CustomerWithContacts>

    fun customersByRole(): List<Pair<ApplicationContactType, CustomerWithContacts>>

    /**
     * Returns a set of email addresses from customer contact persons that:
     * - are not null, empty or blank.
     * - do not match the optional [omit] argument.
     */
    fun contactPersonEmails(omit: String? = null): Set<String> =
        customersWithContacts()
            .flatMap { customer -> customer.contacts }
            .mapNotNull { it.email }
            .filter { it.isNotBlank() }
            .toMutableSet()
            .apply { omit?.let { remove(it) } }
}

@JsonView(ChangeLogView::class)
data class CableReportApplicationData(
    @JsonView(NotInChangeLogView::class) override val applicationType: ApplicationType,
    override val pendingOnClient: Boolean,
    override val name: String,
    val postalAddress: PostalAddress? = null,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val propertyConnectivity: Boolean = false,
    val emergencyWork: Boolean = false,
    val rockExcavation: Boolean?,
    val workDescription: String,
    override val startTime: ZonedDateTime?,
    override val endTime: ZonedDateTime?,
    override val areas: List<ApplicationArea>?,
    override val customerWithContacts: CustomerWithContacts?,
    val contractorWithContacts: CustomerWithContacts?,
    val propertyDeveloperWithContacts: CustomerWithContacts? = null,
    override val representativeWithContacts: CustomerWithContacts? = null,
) : ApplicationData {
    override fun copy(pendingOnClient: Boolean): CableReportApplicationData =
        copy(applicationType = applicationType, pendingOnClient = pendingOnClient)

    override fun toAlluData(hankeTunnus: String): AlluCableReportApplicationData =
        ApplicationDataMapper.toAlluData(hankeTunnus, this)

    /** Returns CustomerWithContacts fields that are not null. */
    override fun customersWithContacts(): List<CustomerWithContacts> =
        listOfNotNull(
            customerWithContacts,
            contractorWithContacts,
            propertyDeveloperWithContacts,
            representativeWithContacts
        )

    override fun customersByRole(): List<Pair<ApplicationContactType, CustomerWithContacts>> =
        listOfNotNull(
            customerWithContacts?.let { ApplicationContactType.HAKIJA to it },
            contractorWithContacts?.let { ApplicationContactType.TYON_SUORITTAJA to it },
            representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
            propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
        )

    fun findOrderer(): Contact? =
        customersWithContacts().flatMap { it.contacts }.find { it.orderer }

    fun toHakemusData(
        yhteystiedot: Map<ApplicationContactType, Hakemusyhteystieto>
    ): JohtoselvityshakemusData =
        JohtoselvityshakemusData(
            name = name,
            postalAddress = postalAddress,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            propertyConnectivity = propertyConnectivity,
            emergencyWork = emergencyWork,
            rockExcavation = rockExcavation,
            workDescription = workDescription,
            startTime = startTime,
            endTime = endTime,
            pendingOnClient = pendingOnClient,
            areas = areas,
            customerWithContacts = yhteystiedot[ApplicationContactType.HAKIJA],
            contractorWithContacts = yhteystiedot[ApplicationContactType.TYON_SUORITTAJA],
            propertyDeveloperWithContacts = yhteystiedot[ApplicationContactType.RAKENNUTTAJA],
            representativeWithContacts = yhteystiedot[ApplicationContactType.ASIANHOITAJA],
        )
}

@JsonView(ChangeLogView::class)
data class ExcavationNotificationData(
    @JsonView(NotInChangeLogView::class) override val applicationType: ApplicationType,
    override val pendingOnClient: Boolean,
    override val name: String,
    val workDescription: String,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val emergencyWork: Boolean = false,
    val cableReportDone: Boolean,
    val rockExcavation: Boolean?, // pakollinen, jos cableReportDone == false
    val cableReports: List<String>? = null, // johtoselvityshakemukset
    val placementContracts: List<String>? = null, // sijoitussopimustunnukset
    val requiredCompetence: Boolean? = false, // oltava true, jotta voi lähettää
    override val startTime: ZonedDateTime?,
    override val endTime: ZonedDateTime?,
    override val areas: List<ApplicationArea>?,
    override val customerWithContacts: CustomerWithContacts?,
    val contractorWithContacts: CustomerWithContacts?,
    val propertyDeveloperWithContacts: CustomerWithContacts? = null,
    override val representativeWithContacts: CustomerWithContacts? = null,
    val invoicingCustomer: InvoicingCustomer? = null,
    val customerReference: String? = null,
    val additionalInfo: String? = null,
) : ApplicationData {
    override fun copy(pendingOnClient: Boolean): ExcavationNotificationData =
        copy(applicationType = applicationType, pendingOnClient = pendingOnClient)

    override fun toAlluData(hankeTunnus: String): AlluExcavationNotificationData =
        ApplicationDataMapper.toAlluData(hankeTunnus, this)

    /** Returns CustomerWithContacts fields that are not null. */
    override fun customersWithContacts(): List<CustomerWithContacts> =
        listOfNotNull(
            customerWithContacts,
            contractorWithContacts,
            propertyDeveloperWithContacts,
            representativeWithContacts
        )

    override fun customersByRole(): List<Pair<ApplicationContactType, CustomerWithContacts>> =
        listOfNotNull(
            customerWithContacts?.let { ApplicationContactType.HAKIJA to it },
            contractorWithContacts?.let { ApplicationContactType.TYON_SUORITTAJA to it },
            representativeWithContacts?.let { ApplicationContactType.ASIANHOITAJA to it },
            propertyDeveloperWithContacts?.let { ApplicationContactType.RAKENNUTTAJA to it },
        )

    fun findOrderer(): Contact? =
        customersWithContacts().flatMap { it.contacts }.find { it.orderer }

    fun toHakemusData(yhteystiedot: Map<ApplicationContactType, Hakemusyhteystieto>): HakemusData =
        KaivuilmoitusData(
            pendingOnClient = pendingOnClient,
            name = name,
            workDescription = workDescription,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            cableReportDone = cableReportDone,
            rockExcavation = rockExcavation,
            cableReports = cableReports,
            placementContracts = placementContracts,
            startTime = startTime,
            endTime = endTime,
            areas = areas,
            customerWithContacts = yhteystiedot[ApplicationContactType.HAKIJA],
            contractorWithContacts = yhteystiedot[ApplicationContactType.TYON_SUORITTAJA],
            propertyDeveloperWithContacts = yhteystiedot[ApplicationContactType.RAKENNUTTAJA],
            representativeWithContacts = yhteystiedot[ApplicationContactType.ASIANHOITAJA],
            invoicingCustomer = invoicingCustomer.toLaskutusyhteystieto(customerReference),
            additionalInfo = additionalInfo,
        )
}

fun List<CustomerWithContacts>.ordererCount() = flatMap { it.contacts }.count { it.orderer }

fun InvoicingCustomer?.toLaskutusyhteystieto(customerReference: String?): Laskutusyhteystieto? =
    this?.let {
        Laskutusyhteystieto(
            it.type!!,
            it.name,
            it.registryKey,
            it.ovt,
            it.invoicingOperator,
            customerReference,
            it.postalAddress?.streetAddress?.streetName,
            it.postalAddress?.postalCode,
            it.postalAddress?.city,
            it.email,
            it.phone
        )
    }

class AlluDataException(path: String, error: AlluDataError) :
    RuntimeException("Application data failed validation at $path: $error")

enum class AlluDataError(private val errorDescription: String) {
    NULL("Can't be null"),
    EMPTY_OR_NULL("Can't be empty or null"),
    ;

    override fun toString(): String = errorDescription
}
