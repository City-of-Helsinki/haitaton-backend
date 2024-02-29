package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HakemusFactory(
    private val hakemusService: HakemusService,
    private val applicationRepository: ApplicationRepository,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {
    fun builder(userId: String, hankeEntity: HankeEntity): HakemusBuilder {
        val hakemus = createHakemus(hankeTunnus = hankeEntity.hankeTunnus)
        return HakemusBuilder(
            hakemus,
            userId,
            hakemusService,
            applicationRepository,
            hankeKayttajaFactory,
            hakemusyhteystietoRepository,
            hakemusyhteyshenkiloRepository,
        )
    }

    fun createHakemus(
        id: Long? = 1,
        alluid: Int? = null,
        alluStatus: ApplicationStatus? = null,
        applicationIdentifier: String? = null,
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        applicationData: HakemusData = createJohtoselvityshakemusData(),
        hankeTunnus: String = "HAI-1234",
    ): Hakemus =
        Hakemus(
            id = id,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            applicationData = applicationData,
            hankeTunnus = hankeTunnus
        )

    fun createJohtoselvityshakemusData(
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        postalAddress: PostalAddress? = null,
        rockExcavation: Boolean = false,
        workDescription: String = "Work description.",
        startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
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
            areas = areas,
            customerWithContacts = customerWithContacts,
            contractorWithContacts = contractorWithContacts,
            representativeWithContacts = representativeWithContacts,
            propertyDeveloperWithContacts = propertyDeveloperWithContacts,
        )
}
