package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.checkChange
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.domain.Loggable
import fi.hel.haitaton.hanke.valmistumisilmoitus.Valmistumisilmoitus
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

enum class ApplicationType {
    CABLE_REPORT,
    EXCAVATION_NOTIFICATION,
}

private val ZERO_UUID = UUID(0, 0)

data class Hakemus(
    override val id: Long,
    override val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    override val applicationIdentifier: String?,
    override val applicationType: ApplicationType,
    val applicationData: HakemusData,
    val hankeTunnus: String,
    val hankeId: Int,
    val valmistumisilmoitukset: Map<ValmistumisilmoitusType, List<Valmistumisilmoitus>>,
) : HakemusIdentifier {
    fun toResponse(): HakemusResponse =
        HakemusResponse(
            id = id,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            applicationData = applicationData.toResponse(),
            hankeTunnus = hankeTunnus,
            valmistumisilmoitukset =
                if (applicationType == ApplicationType.EXCAVATION_NOTIFICATION) {
                    valmistumisilmoitukset.mapValues { (_, values) ->
                        values.map { it.toResponse() }
                    }
                } else {
                    null
                },
        )

    fun toMetadata(): HakemusMetaData =
        HakemusMetaData(
            id = id,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            hankeTunnus = hankeTunnus,
        )
}

private fun <D : HakemusData> D.checkChangeInYhteystieto(
    property: KProperty1<D, Hakemusyhteystieto?>,
    second: D,
): String? =
    if (hasChanges(property.get(this), property.get(second))) {
        property.name
    } else null

/** Compare two yhteystieto ignoring differences in IDs and yhteyshenkilo IDs. */
private fun hasChanges(first: Hakemusyhteystieto?, second: Hakemusyhteystieto?): Boolean {
    fun Hakemusyhteystieto.resetIds() =
        copy(id = ZERO_UUID, yhteyshenkilot = yhteyshenkilot.map { it.copy(id = ZERO_UUID) })

    if (first == null && second == null) return false
    if (first == null || second == null) return true

    return first.resetIds() != second.resetIds()
}

private fun checkChangesInAreas(
    firstAreas: List<Hakemusalue>,
    secondAreas: List<Hakemusalue>,
): List<String> {
    val changedElementsInFirst =
        firstAreas.withIndex().flatMap { (i, area) ->
            area.listChanges("areas[$i]", secondAreas.getOrNull(i))
        }
    val elementsInSecondButNotFirst = secondAreas.indices.drop(firstAreas.size).map { "areas[$it]" }
    return (changedElementsInFirst + elementsInSecondButNotFirst)
}

sealed interface HakemusData {
    val applicationType: ApplicationType
    val name: String
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<Hakemusalue>?
    val paperDecisionReceiver: PaperDecisionReceiver?
    val customerWithContacts: Hakemusyhteystieto?

    fun toResponse(): HakemusDataResponse

    fun yhteystiedot(): List<Hakemusyhteystieto>

    fun listChanges(other: HakemusData): List<String> {
        if (this::class != other::class) {
            throw IncompatibleHakemusDataException(this::class, other::class)
        }

        val changes = mutableListOf<String>()
        checkChange(HakemusData::applicationType, other)?.let { changes.add(it) }
        checkChange(HakemusData::name, other)?.let { changes.add(it) }
        checkChange(HakemusData::startTime, other)?.let { changes.add(it) }
        checkChange(HakemusData::endTime, other)?.let { changes.add(it) }
        checkChange(HakemusData::paperDecisionReceiver, other)?.let { changes.add(it) }
        checkChangeInYhteystieto(HakemusData::customerWithContacts, other)?.let { changes.add(it) }
        changes.addAll(checkChangesInAreas(areas ?: listOf(), other.areas ?: listOf()))

        return changes
    }
}

data class JohtoselvityshakemusData(
    override val applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
    override val name: String,
    val postalAddress: PostalAddress?,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val propertyConnectivity: Boolean = false,
    val emergencyWork: Boolean = false,
    val rockExcavation: Boolean? = null,
    val workDescription: String? = null,
    override val startTime: ZonedDateTime? = null,
    override val endTime: ZonedDateTime? = null,
    override val areas: List<JohtoselvitysHakemusalue>? = null,
    override val paperDecisionReceiver: PaperDecisionReceiver?,
    override val customerWithContacts: Hakemusyhteystieto? = null,
    val contractorWithContacts: Hakemusyhteystieto? = null,
    val propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
    val representativeWithContacts: Hakemusyhteystieto? = null,
) : HakemusData {
    override fun toResponse(): JohtoselvitysHakemusDataResponse =
        JohtoselvitysHakemusDataResponse(
            applicationType = ApplicationType.CABLE_REPORT,
            name = name,
            postalAddress = postalAddress,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            propertyConnectivity = propertyConnectivity,
            emergencyWork = emergencyWork,
            rockExcavation = rockExcavation,
            workDescription = workDescription ?: "",
            startTime = startTime,
            endTime = endTime,
            areas = areas ?: listOf(),
            paperDecisionReceiver = paperDecisionReceiver,
            customerWithContacts = customerWithContacts?.toResponse(),
            contractorWithContacts = contractorWithContacts?.toResponse(),
            propertyDeveloperWithContacts = propertyDeveloperWithContacts?.toResponse(),
            representativeWithContacts = representativeWithContacts?.toResponse(),
        )

    override fun yhteystiedot(): List<Hakemusyhteystieto> =
        listOfNotNull(
            customerWithContacts,
            contractorWithContacts,
            propertyDeveloperWithContacts,
            representativeWithContacts,
        )

    override fun listChanges(other: HakemusData): List<String> {
        val changes = super.listChanges(other).toMutableList()

        other as JohtoselvityshakemusData
        checkChange(JohtoselvityshakemusData::postalAddress, other)?.let { changes.add(it) }
        checkChange(JohtoselvityshakemusData::constructionWork, other)?.let { changes.add(it) }
        checkChange(JohtoselvityshakemusData::maintenanceWork, other)?.let { changes.add(it) }
        checkChange(JohtoselvityshakemusData::propertyConnectivity, other)?.let { changes.add(it) }
        checkChange(JohtoselvityshakemusData::emergencyWork, other)?.let { changes.add(it) }
        checkChange(JohtoselvityshakemusData::rockExcavation, other)?.let { changes.add(it) }
        checkChange(JohtoselvityshakemusData::workDescription, other)?.let { changes.add(it) }
        checkChangeInYhteystieto(JohtoselvityshakemusData::contractorWithContacts, other)?.let {
            changes.add(it)
        }
        checkChangeInYhteystieto(JohtoselvityshakemusData::propertyDeveloperWithContacts, other)
            ?.let { changes.add(it) }
        checkChangeInYhteystieto(JohtoselvityshakemusData::representativeWithContacts, other)?.let {
            changes.add(it)
        }

        return changes
    }
}

data class KaivuilmoitusData(
    override val applicationType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
    override val name: String,
    val workDescription: String,
    val constructionWork: Boolean,
    val maintenanceWork: Boolean,
    val emergencyWork: Boolean,
    val cableReportDone: Boolean,
    val rockExcavation: Boolean?,
    val cableReports: List<String>?,
    val placementContracts: List<String>?,
    val requiredCompetence: Boolean,
    override val startTime: ZonedDateTime?,
    override val endTime: ZonedDateTime?,
    override val areas: List<KaivuilmoitusAlue>?,
    override val paperDecisionReceiver: PaperDecisionReceiver?,
    override val customerWithContacts: Hakemusyhteystieto?,
    val contractorWithContacts: Hakemusyhteystieto?,
    val propertyDeveloperWithContacts: Hakemusyhteystieto?,
    val representativeWithContacts: Hakemusyhteystieto?,
    val invoicingCustomer: Laskutusyhteystieto?,
    val additionalInfo: String?,
) : HakemusData {
    override fun toResponse(): KaivuilmoitusDataResponse =
        KaivuilmoitusDataResponse(
            applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
            name = name,
            workDescription = workDescription,
            constructionWork = constructionWork,
            maintenanceWork = maintenanceWork,
            emergencyWork = emergencyWork,
            cableReportDone = cableReportDone,
            rockExcavation = rockExcavation,
            cableReports = cableReports,
            placementContracts = placementContracts,
            requiredCompetence = requiredCompetence,
            startTime = startTime,
            endTime = endTime,
            areas = areas ?: listOf(),
            paperDecisionReceiver = paperDecisionReceiver,
            customerWithContacts = customerWithContacts?.toResponse(),
            contractorWithContacts = contractorWithContacts?.toResponse(),
            propertyDeveloperWithContacts = propertyDeveloperWithContacts?.toResponse(),
            representativeWithContacts = representativeWithContacts?.toResponse(),
            invoicingCustomer = invoicingCustomer?.toResponse(),
            additionalInfo = additionalInfo,
        )

    override fun yhteystiedot(): List<Hakemusyhteystieto> =
        listOfNotNull(
            customerWithContacts,
            contractorWithContacts,
            propertyDeveloperWithContacts,
            representativeWithContacts,
        )

    override fun listChanges(other: HakemusData): List<String> {
        val changes = super.listChanges(other).toMutableList()

        other as KaivuilmoitusData
        checkChange(KaivuilmoitusData::workDescription, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::constructionWork, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::maintenanceWork, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::emergencyWork, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::cableReportDone, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::rockExcavation, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::cableReports, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::placementContracts, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::requiredCompetence, other)?.let { changes.add(it) }
        checkChangeInYhteystieto(KaivuilmoitusData::contractorWithContacts, other)?.let {
            changes.add(it)
        }
        checkChangeInYhteystieto(KaivuilmoitusData::propertyDeveloperWithContacts, other)?.let {
            changes.add(it)
        }
        checkChangeInYhteystieto(KaivuilmoitusData::representativeWithContacts, other)?.let {
            changes.add(it)
        }
        checkChange(KaivuilmoitusData::invoicingCustomer, other)?.let { changes.add(it) }
        checkChange(KaivuilmoitusData::additionalInfo, other)?.let { changes.add(it) }

        return changes
    }
}

interface HakemusIdentifier : HasId<Long>, Loggable {
    override val id: Long
    val alluid: Int?
    val applicationIdentifier: String?
    val applicationType: ApplicationType

    override fun logString() =
        "Hakemus: (id=$id, alluId=$alluid, identifier=$applicationIdentifier, type=$applicationType)"
}

/** Without application data, just the identifiers and metadata. */
data class HakemusMetaData(
    override val id: Long,
    override val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    override val applicationIdentifier: String?,
    override val applicationType: ApplicationType,
    val hankeTunnus: String,
) : HakemusIdentifier

class IncompatibleHakemusDataException(firstClass: KClass<*>, secondClass: KClass<*>) :
    RuntimeException(
        "Incompatible hakemus data when retrieving changes. firstClass=${firstClass.simpleName}, secondClass=${secondClass.simpleName}"
    )
