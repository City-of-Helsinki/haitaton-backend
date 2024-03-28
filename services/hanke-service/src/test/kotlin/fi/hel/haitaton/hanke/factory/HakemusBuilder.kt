package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.ExcavationNotificationApplicationData
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import java.time.ZonedDateTime

data class HakemusBuilder(
    private var applicationEntity: ApplicationEntity,
    private var hankeId: Int,
    private val userId: String,
    private val hakemusFactory: HakemusFactory,
    private val hakemusService: HakemusService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val applicationRepository: ApplicationRepository,
    private val hankeRepository: HankeRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    fun save(): Hakemus = hakemusService.getById(saveEntity().id!!)

    fun saveEntity(): ApplicationEntity {
        val savedApplication = applicationRepository.save(applicationEntity)
        savedApplication.yhteystiedot.forEach { (_, yhteystieto) ->
            yhteystieto.yhteyshenkilot.forEach { yhteyshenkilo ->
                hakemusyhteyshenkiloRepository.save(yhteyshenkilo)
            }
        }
        return savedApplication
    }

    fun withStatus(
        status: ApplicationStatus = ApplicationStatus.PENDING,
        alluId: Int = 1,
        identifier: String = "JS000$alluId",
    ): HakemusBuilder {
        applicationEntity.apply {
            alluid = alluId
            alluStatus = status
            applicationIdentifier = identifier
        }
        return this
    }

    fun inHandling(alluId: Int = 1) = withStatus(ApplicationStatus.HANDLING, alluId)

    fun withName(name: String): HakemusBuilder =
        updateApplicationData(
            { copy(name = name) },
            { copy(name = name) },
        )

    fun withWorkDescription(workDescription: String): HakemusBuilder =
        updateApplicationData(
            { copy(workDescription = workDescription) },
            { copy(workDescription = workDescription) },
        )

    fun withPendingOnClient(pendingOnClient: Boolean) =
        updateApplicationData(
            { copy(pendingOnClient = pendingOnClient) },
            { copy(pendingOnClient = pendingOnClient) },
        )

    fun withArea(area: ApplicationArea = ApplicationFactory.createApplicationArea()) =
        updateApplicationData(
            { copy(areas = (areas ?: listOf()).plus(area)) },
            { copy(areas = (areas ?: listOf()).plus(area)) },
        )

    fun withStartTime(time: ZonedDateTime? = DateFactory.getStartDatetime()) =
        updateApplicationData(
            { copy(startTime = time) },
            { copy(startTime = time) },
        )

    fun withEndTime(time: ZonedDateTime? = DateFactory.getEndDatetime()) =
        updateApplicationData(
            { copy(endTime = time) },
            { copy(endTime = time) },
        )

    fun withRockExcavation(rockExcavation: Boolean?) =
        updateApplicationData(
            { copy(rockExcavation = rockExcavation) },
            { copy(rockExcavation = rockExcavation) },
        )

    private fun updateApplicationData(
        onCableReport: CableReportApplicationData.() -> CableReportApplicationData,
        onExcavationNotification:
            ExcavationNotificationApplicationData.() -> ExcavationNotificationApplicationData,
    ) = apply {
        applicationEntity.applicationData =
            when (applicationEntity.applicationType) {
                ApplicationType.CABLE_REPORT -> {
                    (applicationEntity.applicationData as CableReportApplicationData)
                        .onCableReport()
                }
                ApplicationType.EXCAVATION_NOTIFICATION -> {
                    (applicationEntity.applicationData as ExcavationNotificationApplicationData)
                        .onExcavationNotification()
                }
            }
    }

    /** Set all the fields that need to be set for the application to be valid for sending. */
    fun withMandatoryFields() =
        withName(ApplicationFactory.DEFAULT_APPLICATION_NAME)
            .withWorkDescription(ApplicationFactory.DEFAULT_WORK_DESCRIPTION)
            .withStartTime()
            .withEndTime()
            .withArea()
            .withRockExcavation(false)
            .hakija()
            .tyonSuorittaja(founder())

    private fun founder(): HankekayttajaEntity =
        hankeKayttajaService.getKayttajaByUserId(hankeId, userId)!!

    fun hakija(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA))
    ): HakemusBuilder = yhteystieto(ApplicationContactType.HAKIJA, yhteystieto, *yhteyshenkilot)

    fun hakija(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = true,
    ): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.HAKIJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA,
        )

    fun hakija(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusBuilder = hakija(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun tyonSuorittaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA))
    ): HakemusBuilder =
        yhteystieto(ApplicationContactType.TYON_SUORITTAJA, yhteystieto, *yhteyshenkilot)

    fun tyonSuorittaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.TYON_SUORITTAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA,
        )

    fun tyonSuorittaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusBuilder = tyonSuorittaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun rakennuttaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA))
    ): HakemusBuilder =
        yhteystieto(ApplicationContactType.RAKENNUTTAJA, yhteystieto, *yhteyshenkilot)

    fun rakennuttaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.RAKENNUTTAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA,
        )

    fun rakennuttaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusBuilder = rakennuttaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun asianhoitaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA))
    ): HakemusBuilder =
        yhteystieto(ApplicationContactType.ASIANHOITAJA, yhteystieto, *yhteyshenkilot)

    fun asianhoitaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.ASIANHOITAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA,
        )

    fun asianhoitaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusBuilder = asianhoitaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    private fun yhteystieto(
        rooli: ApplicationContactType,
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusBuilder {
        val yhteystietoEntity = createYhteystietoEntity(rooli, yhteystieto)
        val yhteyshenkiloEntities =
            yhteyshenkilot.map { createYhteyshenkiloEntity(yhteystietoEntity, it) }
        yhteystietoEntity.yhteyshenkilot.addAll(yhteyshenkiloEntities)
        applicationEntity.yhteystiedot[rooli] = yhteystietoEntity

        return this
    }

    private fun yhteystieto(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean,
        rooli: ApplicationContactType,
        kayttajaInput: HankekayttajaInput
    ): HakemusBuilder {
        val yhteystietoEntity = createYhteystietoEntity(rooli, HakemusyhteystietoFactory.create())
        val kayttaja = kayttaja(kayttajaInput, kayttooikeustaso)
        val yhteyshenkiloEntity = createYhteyshenkiloEntity(yhteystietoEntity, kayttaja, tilaaja)

        yhteystietoEntity.yhteyshenkilot.add(yhteyshenkiloEntity)
        applicationEntity.yhteystiedot[rooli] = yhteystietoEntity
        return this
    }

    private fun kayttaja(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankekayttajaEntity =
        hankeKayttajaFactory.findOrSaveIdentifiedUser(
            applicationEntity.hanke.id,
            kayttajaInput,
            kayttooikeustaso = kayttooikeustaso,
        )

    private fun createYhteystietoEntity(
        rooli: ApplicationContactType,
        yhteystieto: Hakemusyhteystieto
    ): HakemusyhteystietoEntity =
        HakemusyhteystietoEntity(
            tyyppi = yhteystieto.tyyppi,
            rooli = rooli,
            nimi = yhteystieto.nimi,
            sahkoposti = yhteystieto.sahkoposti,
            puhelinnumero = yhteystieto.puhelinnumero,
            ytunnus = yhteystieto.ytunnus,
            application = applicationEntity,
        )

    private fun createYhteyshenkiloEntity(
        yhteystietoEntity: HakemusyhteystietoEntity,
        kayttaja: HankekayttajaEntity,
        tilaaja: Boolean = false
    ) =
        HakemusyhteyshenkiloEntity(
            hankekayttaja = kayttaja,
            hakemusyhteystieto = yhteystietoEntity,
            tilaaja = tilaaja
        )
}
