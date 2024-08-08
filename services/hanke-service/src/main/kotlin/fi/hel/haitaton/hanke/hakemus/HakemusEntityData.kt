package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
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
    JsonSubTypes.Type(value = JohtoselvityshakemusEntityData::class, name = "CABLE_REPORT"),
    JsonSubTypes.Type(value = KaivuilmoitusEntityData::class, name = "EXCAVATION_NOTIFICATION"),
)
sealed interface HakemusEntityData {
    val applicationType: ApplicationType
    val pendingOnClient: Boolean
    val name: String
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<Hakemusalue>?

    fun copy(pendingOnClient: Boolean): HakemusEntityData
}

@JsonView(ChangeLogView::class)
data class JohtoselvityshakemusEntityData(
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
) : HakemusEntityData {
    override fun copy(pendingOnClient: Boolean): JohtoselvityshakemusEntityData =
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
data class KaivuilmoitusEntityData(
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
) : HakemusEntityData {
    override fun copy(pendingOnClient: Boolean): KaivuilmoitusEntityData =
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

    fun toJohtoselvityshakemusEntityData(): JohtoselvityshakemusEntityData =
        JohtoselvityshakemusEntityData(
            applicationType = ApplicationType.CABLE_REPORT,
            pendingOnClient = pendingOnClient,
            name = name,
            postalAddress = areas.toPostalAddress(),
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            propertyConnectivity = false,
            emergencyWork = emergencyWork,
            rockExcavation = rockExcavation,
            workDescription = workDescription,
            startTime = startTime,
            endTime = endTime,
            areas = areas?.flatMap { it.toJohtoselvitysHakemusalues() },
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
