package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.HankePerustaja
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.test.USERNAME
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HakemusFactory(
    private val hankeKayttajaService: HankeKayttajaService,
    private val hakemusService: HakemusService,
    private val applicationRepository: ApplicationRepository,
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

    fun builder(
        hankeEntity: HankeEntity = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
    ) = builder(USERNAME, hankeEntity)

    private fun builder(
        userId: String,
        applicationEntity: ApplicationEntity,
        hankeId: Int,
    ): HakemusBuilder =
        HakemusBuilder(
            applicationEntity,
            hankeId,
            userId,
            this,
            hakemusService,
            hankeKayttajaService,
            applicationRepository,
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
        val applicationEntity = createEntity(userId = userId, hanke = hankeEntity)

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
            )

        private fun createHakemusData(type: ApplicationType): HakemusData =
            when (type) {
                ApplicationType.CABLE_REPORT -> createJohtoselvityshakemusData()
                ApplicationType.EXCAVATION_NOTIFICATION -> createKaivuilmoitusData()
            }

        private fun createJohtoselvityshakemusData(
            name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
            postalAddress: PostalAddress? = null,
            rockExcavation: Boolean = false,
            workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            areas: List<ApplicationArea>? = listOf(ApplicationFactory.createApplicationArea()),
            customerWithContacts: Hakemusyhteystieto? = null,
            contractorWithContacts: Hakemusyhteystieto? = null,
            representativeWithContacts: Hakemusyhteystieto? = null,
            propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
        ): JohtoselvityshakemusData =
            JohtoselvityshakemusData(
                name = name,
                postalAddress = postalAddress,
                constructionWork = false,
                maintenanceWork = false,
                propertyConnectivity = false,
                emergencyWork = false,
                rockExcavation = rockExcavation,
                workDescription = workDescription,
                startTime = startTime,
                endTime = endTime,
                pendingOnClient = pendingOnClient,
                areas = areas,
                customerWithContacts = customerWithContacts,
                contractorWithContacts = contractorWithContacts,
                representativeWithContacts = representativeWithContacts,
                propertyDeveloperWithContacts = propertyDeveloperWithContacts,
            )

        private fun createKaivuilmoitusData(
            pendingOnClient: Boolean = false,
            name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
            workDescription: String = "Work description.",
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            areas: List<ApplicationArea>? = listOf(ApplicationFactory.createApplicationArea()),
            customerWithContacts: Hakemusyhteystieto? = null,
            contractorWithContacts: Hakemusyhteystieto? = null,
            representativeWithContacts: Hakemusyhteystieto? = null,
            propertyDeveloperWithContacts: Hakemusyhteystieto? = null,
        ): KaivuilmoitusData =
            KaivuilmoitusData(
                pendingOnClient = pendingOnClient,
                name = name,
                workDescription = workDescription,
                startTime = startTime,
                endTime = endTime,
                areas = areas,
                customerWithContacts = customerWithContacts,
                contractorWithContacts = contractorWithContacts,
                representativeWithContacts = representativeWithContacts,
                propertyDeveloperWithContacts = propertyDeveloperWithContacts,
            )

        fun createEntity(
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            userId: String,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: ApplicationData =
                ApplicationFactory.createBlankApplicationData(applicationType),
            hanke: HankeEntity,
        ): ApplicationEntity =
            ApplicationEntity(
                id = null,
                alluid = alluid,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                userId = userId,
                applicationType = applicationType,
                applicationData = applicationData,
                hanke = hanke,
            )
    }
}
