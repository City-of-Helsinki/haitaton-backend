package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusWithExtras
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusMuutosilmoitusResponse
import fi.hel.haitaton.hanke.hakemus.HankkeenHakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.hakemus.Laskutusyhteystieto
import fi.hel.haitaton.hanke.hakemus.PaperDecisionReceiver
import fi.hel.haitaton.hanke.hakemus.PostalAddress
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.paatos.Paatos
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.taydennys.TaydennysWithExtras
import fi.hel.haitaton.hanke.taydennys.Taydennyspyynto
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.valmistumisilmoitus.Valmistumisilmoitus
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HakemusFactory(
    private val hankeKayttajaService: HankeKayttajaService,
    private val hakemusService: HakemusService,
    private val hakemusRepository: HakemusRepository,
    private val hankeRepository: HankeRepository,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    private val hankeFactory: HankeFactory,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {
    fun builder(
        userId: String,
        hankeEntity: HankeEntity = hankeFactory.builder(userId).withHankealue().saveEntity(),
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
    ): HakemusBuilder {
        val applicationEntity =
            createEntity(userId = userId, hanke = hankeEntity, applicationType = applicationType)
        return builder(userId, applicationEntity, hankeEntity.id)
    }

    fun builder(hankeEntity: HankeEntity, applicationType: ApplicationType): HakemusBuilder =
        builder(USERNAME, hankeEntity, applicationType)

    fun builder(
        hankeEntity: HankeEntity = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
    ) = builder(USERNAME, hankeEntity)

    fun builder(applicationType: ApplicationType) =
        builder(
            USERNAME,
            hankeFactory.builder(USERNAME).withHankealue().saveEntity(),
            applicationType,
        )

    private fun builder(
        userId: String,
        hakemusEntity: HakemusEntity,
        hankeId: Int,
    ): HakemusBuilder =
        HakemusBuilder(
            hakemusEntity,
            hankeId,
            userId,
            this,
            hakemusService,
            hankeKayttajaService,
            hakemusRepository,
            hankeRepository,
            hankeKayttajaFactory,
            hakemusyhteystietoRepository,
            hakemusyhteyshenkiloRepository,
        )

    fun builderWithGeneratedHanke(
        userId: String = USERNAME,
        nimi: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        perustaja: HankePerustaja = HankeFactory.DEFAULT_HANKE_PERUSTAJA,
    ): HakemusBuilder {
        val request = CreateHankeRequest(nimi, perustaja)
        val hankeEntity = hankeFactory.saveGenerated(request, userId)
        val applicationEntity =
            createEntity(
                userId = userId,
                hanke = hankeEntity,
                applicationType = ApplicationType.CABLE_REPORT,
            )

        return builder(userId, applicationEntity, hankeEntity.id)
    }

    companion object {
        fun create(
            id: Long = 1,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: HakemusData = createHakemusData(applicationType),
            hankeTunnus: String = "HAI-1234",
            hankeId: Int = 1,
            valmistumisilmoitukset: List<Valmistumisilmoitus> = listOf(),
        ): Hakemus =
            Hakemus(
                id = id,
                alluid = alluid,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                applicationType = applicationType,
                applicationData = applicationData,
                hankeTunnus = hankeTunnus,
                hankeId = hankeId,
                valmistumisilmoitukset = valmistumisilmoitukset.groupBy { it.type },
            )

        fun createSeveralHankkeenHakemusResponses(
            includeAreas: Boolean
        ): List<HankkeenHakemusResponse> =
            listOf(
                createHankkeenHakemusResponse(
                    1,
                    ApplicationType.CABLE_REPORT,
                    MuutosilmoitusFactory.createEntity(),
                    includeAreas,
                ),
                createHankkeenHakemusResponse(2, ApplicationType.CABLE_REPORT, null, includeAreas),
                createHankkeenHakemusResponse(
                    3,
                    ApplicationType.EXCAVATION_NOTIFICATION,
                    MuutosilmoitusFactory.createEntity(),
                    includeAreas,
                ),
                createHankkeenHakemusResponse(
                    4,
                    ApplicationType.EXCAVATION_NOTIFICATION,
                    null,
                    includeAreas,
                ),
            )

        fun createHankkeenHakemusResponse(
            id: Long = 1,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            muutosilmoitus: MuutosilmoitusEntity? = null,
            includeAreas: Boolean = false,
        ): HankkeenHakemusResponse {
            val data =
                when (applicationType) {
                    ApplicationType.CABLE_REPORT ->
                        HankkeenHakemusDataResponse(
                            ApplicationFactory.createCableReportApplicationData(),
                            includeAreas,
                        )
                    ApplicationType.EXCAVATION_NOTIFICATION ->
                        HankkeenHakemusDataResponse(
                            ApplicationFactory.createExcavationNotificationData(),
                            includeAreas,
                        )
                }

            return HankkeenHakemusResponse(
                id = id,
                alluid = null,
                alluStatus = null,
                applicationIdentifier = null,
                applicationType = applicationType,
                applicationData = data,
                muutosilmoitus =
                    muutosilmoitus?.let { HankkeenHakemusMuutosilmoitusResponse(it, includeAreas) },
                paatokset = mapOf(),
            )
        }

        fun createHakemusData(type: ApplicationType): HakemusData =
            when (type) {
                ApplicationType.CABLE_REPORT -> createJohtoselvityshakemusData()
                ApplicationType.EXCAVATION_NOTIFICATION -> createKaivuilmoitusData()
            }

        fun createJohtoselvityshakemusData(
            name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
            postalAddress: PostalAddress? = null,
            rockExcavation: Boolean = false,
            workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            areas: List<JohtoselvitysHakemusalue>? =
                listOf(ApplicationFactory.createCableReportApplicationArea()),
            paperDecisionReceiver: PaperDecisionReceiver? = null,
            customerWithContacts: Hakemusyhteystieto? = null,
            contractorWithContacts: Hakemusyhteystieto? = null,
            representativeWithContacts: Hakemusyhteystieto? = null,
            propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
            constructionWork: Boolean = false,
            maintenanceWork: Boolean = false,
            propertyConnectivity: Boolean = false,
            emergencyWork: Boolean = false,
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
                areas = areas,
                paperDecisionReceiver = paperDecisionReceiver,
                customerWithContacts = customerWithContacts,
                contractorWithContacts = contractorWithContacts,
                representativeWithContacts = representativeWithContacts,
                propertyDeveloperWithContacts = propertyDeveloperWithContacts,
            )

        fun createKaivuilmoitusData(
            name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
            workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
            constructionWork: Boolean = false,
            maintenanceWork: Boolean = false,
            emergencyWork: Boolean = false,
            cableReportDone: Boolean = true,
            rockExcavation: Boolean? = null,
            requiredCompetence: Boolean = false,
            cableReports: List<String>? = null,
            placementContracts: List<String>? = null,
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            areas: List<KaivuilmoitusAlue>? =
                listOf(ApplicationFactory.createExcavationNotificationArea()),
            paperDecisionReceiver: PaperDecisionReceiver? = null,
            customerWithContacts: Hakemusyhteystieto? = null,
            contractorWithContacts: Hakemusyhteystieto? = null,
            representativeWithContacts: Hakemusyhteystieto? = null,
            propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
            invoicingCustomer: Laskutusyhteystieto? = null,
            additionalInfo: String? = null,
        ): KaivuilmoitusData =
            KaivuilmoitusData(
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
                areas = areas,
                paperDecisionReceiver = paperDecisionReceiver,
                customerWithContacts = customerWithContacts,
                contractorWithContacts = contractorWithContacts,
                representativeWithContacts = representativeWithContacts,
                propertyDeveloperWithContacts = propertyDeveloperWithContacts,
                invoicingCustomer = invoicingCustomer,
                additionalInfo = additionalInfo,
            )

        fun createEntity(
            id: Long = 0,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            userId: String = USERNAME,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            hakemusEntityData: HakemusEntityData =
                ApplicationFactory.createBlankApplicationData(applicationType),
            hanke: HankeEntity = HankeFactory.createEntity(),
        ): HakemusEntity =
            HakemusEntity(
                id = id,
                alluid = alluid,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                userId = userId,
                applicationType = applicationType,
                hakemusEntityData = hakemusEntityData,
                hanke = hanke,
            )

        fun Hakemus.withExtras(
            paatokset: List<Paatos> = listOf(),
            taydennyspyynto: Taydennyspyynto? = null,
            taydennys: TaydennysWithExtras? = null,
            muutosilmoitus: Muutosilmoitus? = null,
        ) = HakemusWithExtras(this, paatokset, taydennyspyynto, taydennys, muutosilmoitus)

        fun hakemusDataForRegistryKeyTest(tyyppi: CustomerType): KaivuilmoitusData {
            val hakija =
                HakemusyhteystietoFactory.createPerson(tyyppi = tyyppi, registryKey = "280341-912F")
            val suorittaja =
                HakemusyhteystietoFactory.createPerson(tyyppi = tyyppi, registryKey = null)
            val rakennuttaja = HakemusyhteystietoFactory.create(registryKey = "5425233-4")
            val asianhoitaja = HakemusyhteystietoFactory.create(registryKey = null)
            val laskutusyhteystieto =
                HakemusyhteystietoFactory.createLaskutusyhteystieto(
                    tyyppi = tyyppi,
                    registryKey = "280341-912F",
                )
            val hakemusdata =
                createKaivuilmoitusData(
                    customerWithContacts = hakija,
                    contractorWithContacts = suorittaja,
                    propertyDeveloperWithContacts = rakennuttaja,
                    representativeWithContacts = asianhoitaja,
                    invoicingCustomer = laskutusyhteystieto,
                )
            return hakemusdata
        }
    }
}
