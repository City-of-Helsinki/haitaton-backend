package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationMetaData
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationArea
import fi.hel.haitaton.hanke.application.ExcavationNotificationArea
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.domain.HasId
import java.time.ZonedDateTime

data class Hakemus(
    override val id: Long,
    override val alluid: Int?,
    val alluStatus: ApplicationStatus?,
    override val applicationIdentifier: String?,
    val applicationType: ApplicationType,
    val applicationData: HakemusData,
    val hankeTunnus: String,
    val hankeId: Int,
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
        )

    fun toMetadata(): ApplicationMetaData =
        ApplicationMetaData(
            id = id,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            hankeTunnus = hankeTunnus
        )
}

sealed interface HakemusData {
    val applicationType: ApplicationType
    val name: String
    val pendingOnClient: Boolean
    val startTime: ZonedDateTime?
    val endTime: ZonedDateTime?
    val areas: List<ApplicationArea>?
    val customerWithContacts: Hakemusyhteystieto?

    fun toResponse(): HakemusDataResponse

    fun yhteystiedot(): List<Hakemusyhteystieto>
}

data class JohtoselvityshakemusData(
    override val applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
    override val name: String,
    val postalAddress: PostalAddress? = null,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val propertyConnectivity: Boolean = false,
    val emergencyWork: Boolean = false,
    val rockExcavation: Boolean? = null,
    val workDescription: String? = null,
    override val startTime: ZonedDateTime? = null,
    override val endTime: ZonedDateTime? = null,
    override val pendingOnClient: Boolean,
    override val areas: List<CableReportApplicationArea>? = null,
    override val customerWithContacts: Hakemusyhteystieto? = null,
    val contractorWithContacts: Hakemusyhteystieto? = null,
    val propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
    val representativeWithContacts: Hakemusyhteystieto? = null,
) : HakemusData {
    override fun toResponse(): JohtoselvitysHakemusDataResponse =
        JohtoselvitysHakemusDataResponse(
            applicationType = ApplicationType.CABLE_REPORT,
            pendingOnClient = pendingOnClient,
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
}

data class KaivuilmoitusData(
    override val applicationType: ApplicationType = ApplicationType.EXCAVATION_NOTIFICATION,
    override val pendingOnClient: Boolean,
    override val name: String,
    val workDescription: String? = null,
    val constructionWork: Boolean = false,
    val maintenanceWork: Boolean = false,
    val emergencyWork: Boolean = false,
    val cableReportDone: Boolean = true,
    val rockExcavation: Boolean? = null,
    val cableReports: List<String>? = null,
    val placementContracts: List<String>? = null,
    val requiredCompetence: Boolean = false,
    override val startTime: ZonedDateTime? = null,
    override val endTime: ZonedDateTime? = null,
    override val areas: List<ExcavationNotificationArea>? = null,
    override val customerWithContacts: Hakemusyhteystieto? = null,
    val contractorWithContacts: Hakemusyhteystieto? = null,
    val propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
    val representativeWithContacts: Hakemusyhteystieto? = null,
    val invoicingCustomer: Laskutusyhteystieto? = null,
    val additionalInfo: String? = null,
) : HakemusData {
    override fun toResponse(): KaivuilmoitusDataResponse =
        KaivuilmoitusDataResponse(
            applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
            pendingOnClient = pendingOnClient,
            name = name,
            workDescription = workDescription ?: "",
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
}

interface HakemusIdentifier : HasId<Long> {
    override val id: Long
    val alluid: Int?
    val applicationIdentifier: String?

    fun logString() = "Hakemus: (id=$id, alluId=$alluid, identifier=$applicationIdentifier)"
}
