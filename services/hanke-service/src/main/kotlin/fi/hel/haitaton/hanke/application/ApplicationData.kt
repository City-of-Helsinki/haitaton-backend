package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
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
    val areas: List<Hakemusalue>?

    fun copy(pendingOnClient: Boolean): ApplicationData
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
    override val areas: List<JohtoselvitysHakemusalue>?,
) : ApplicationData {
    override fun copy(pendingOnClient: Boolean): CableReportApplicationData =
        copy(applicationType = applicationType, pendingOnClient = pendingOnClient)

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
    override val areas: List<KaivuilmoitusAlue>?,
    val invoicingCustomer: InvoicingCustomer? = null,
    val customerReference: String? = null,
    val additionalInfo: String? = null,
) : ApplicationData {
    override fun copy(pendingOnClient: Boolean): ExcavationNotificationData =
        copy(applicationType = applicationType, pendingOnClient = pendingOnClient)

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
            requiredCompetence = requiredCompetence ?: false,
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
