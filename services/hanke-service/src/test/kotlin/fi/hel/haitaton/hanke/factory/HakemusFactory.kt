package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.toHakemusyhteystieto
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import jakarta.transaction.Transactional
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HakemusFactory(
    private val hankeKayttajaService: HankeKayttajaService,
    private val applicationRepository: ApplicationRepository,
    private val hankeRepository: HankeRepository,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {
    fun builder(userId: String, hankeEntity: HankeEntity): HakemusBuilder {
        val hakemus = createHakemus(hankeTunnus = hankeEntity.hankeTunnus)
        return HakemusBuilder(
            hakemus,
            userId,
            this,
            hankeKayttajaService,
            applicationRepository,
            hankeRepository,
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

    @Transactional
    fun save(hakemus: Hakemus, userId: String): Hakemus {
        val hanke =
            hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)
                ?: throw HankeNotFoundException(hakemus.hankeTunnus)
        val applicationEntity = hakemus.toEntity(userId, hanke)
        val savedApplicationEntity = applicationRepository.save(applicationEntity)
        val savedApplication = savedApplicationEntity.toHakemus()
        return savedApplication
    }

    private fun Hakemus.toEntity(userId: String, hanke: HankeEntity): ApplicationEntity =
        ApplicationEntity(
                id = null,
                alluid = alluid,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                userId = userId,
                applicationType = applicationType,
                applicationData = applicationData.toApplicationData(true),
                hanke = hanke
            )
            .also {
                it.yhteystiedot.putAll(
                    when (applicationData) {
                        is JohtoselvityshakemusData ->
                            (applicationData as JohtoselvityshakemusData).yhteystiedot(it)
                    }
                )
            }

    private fun JohtoselvityshakemusData.yhteystiedot(
        applicationEntity: ApplicationEntity
    ): MutableMap<ApplicationContactType, HakemusyhteystietoEntity> {
        val yhteystiedot = mutableMapOf<ApplicationContactType, HakemusyhteystietoEntity>()
        customerWithContacts?.let {
            yhteystiedot[ApplicationContactType.HAKIJA] =
                it.toHakemusyhteystietoEntity(applicationEntity)
        }
        contractorWithContacts?.let {
            yhteystiedot[ApplicationContactType.TYON_SUORITTAJA] =
                it.toHakemusyhteystietoEntity(applicationEntity)
        }
        propertyDeveloperWithContacts?.let {
            yhteystiedot[ApplicationContactType.RAKENNUTTAJA] =
                it.toHakemusyhteystietoEntity(applicationEntity)
        }
        representativeWithContacts?.let {
            yhteystiedot[ApplicationContactType.ASIANHOITAJA] =
                it.toHakemusyhteystietoEntity(applicationEntity)
        }
        return yhteystiedot
    }

    private fun Hakemusyhteystieto.toHakemusyhteystietoEntity(
        applicationEntity: ApplicationEntity
    ): HakemusyhteystietoEntity =
        HakemusyhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                ytunnus = ytunnus,
                application = applicationEntity,
            )
            .also { hakemusyhteystietoEntity ->
                hakemusyhteystietoEntity.yhteyshenkilot.addAll(
                    yhteyshenkilot.map { it.toHakemusyhteyshenkiloEntity(hakemusyhteystietoEntity) }
                )
            }

    private fun Hakemusyhteyshenkilo.toHakemusyhteyshenkiloEntity(
        hakemusyhteystietoEntity: HakemusyhteystietoEntity
    ): HakemusyhteyshenkiloEntity =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = hakemusyhteystietoEntity,
            hankekayttaja =
                hankeKayttajaService.getKayttajaForHanke(
                    hankekayttajaId,
                    hakemusyhteystietoEntity.application.hanke.id
                ),
            tilaaja = tilaaja
        )

    private fun ApplicationEntity.toHakemus(): Hakemus =
        Hakemus(
            id = id!!,
            alluid = alluid,
            alluStatus = alluStatus,
            applicationIdentifier = applicationIdentifier,
            applicationType = applicationType,
            applicationData =
                when (applicationData) {
                    is CableReportApplicationData ->
                        (this.applicationData as CableReportApplicationData).toHakemusData(this)
                },
            hankeTunnus = hanke.hankeTunnus
        )

    private fun CableReportApplicationData.toHakemusData(
        applicationEntity: ApplicationEntity
    ): HakemusData =
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
            customerWithContacts =
                applicationEntity.yhteystiedot[ApplicationContactType.HAKIJA]
                    .toHakemusyhteystieto(),
            contractorWithContacts =
                applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]
                    .toHakemusyhteystieto(),
            propertyDeveloperWithContacts =
                applicationEntity.yhteystiedot[ApplicationContactType.RAKENNUTTAJA]
                    .toHakemusyhteystieto(),
            representativeWithContacts =
                applicationEntity.yhteystiedot[ApplicationContactType.ASIANHOITAJA]
                    .toHakemusyhteystieto(),
        )
}
