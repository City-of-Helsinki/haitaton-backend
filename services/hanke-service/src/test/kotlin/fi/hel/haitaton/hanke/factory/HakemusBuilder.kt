package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.ExcavationAnnouncementApplicationData
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso

data class HakemusBuilder(
    private var applicationEntity: ApplicationEntity,
    private val userId: String,
    private val hakemusFactory: HakemusFactory,
    private val hankeKayttajaService: HankeKayttajaService,
    private val applicationRepository: ApplicationRepository,
    private val hankeRepository: HankeRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    fun save(): ApplicationEntity {
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
        identifier: String = "JS000$alluId"
    ): HakemusBuilder {
        applicationEntity =
            applicationEntity.copy(
                alluid = alluId,
                alluStatus = status,
                applicationIdentifier = identifier
            )
        return this
    }

    fun inHandling(alluId: Int = 1) = withStatus(ApplicationStatus.HANDLING, alluId)

    fun withName(name: String): HakemusBuilder = apply {
        when (applicationEntity.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                onCableReport { copy(name = name) }
            }
            ApplicationType.EXCAVATION_ANNOUNCEMENT -> {
                onExcavationAnnouncement { copy(name = name) }
            }
        }
    }

    fun withWorkDescription(workDescription: String): HakemusBuilder = apply {
        when (applicationEntity.applicationType) {
            ApplicationType.CABLE_REPORT -> {
                onCableReport { copy(workDescription = workDescription) }
            }
            ApplicationType.EXCAVATION_ANNOUNCEMENT -> {
                onExcavationAnnouncement { copy(workDescription = workDescription) }
            }
        }
    }

    private fun onCableReport(f: CableReportApplicationData.() -> CableReportApplicationData) {
        applicationEntity.applicationData =
            (applicationEntity.applicationData as CableReportApplicationData).f()
    }

    private fun onExcavationAnnouncement(
        f: ExcavationAnnouncementApplicationData.() -> ExcavationAnnouncementApplicationData
    ) {
        (applicationEntity.applicationData as ExcavationAnnouncementApplicationData).f()
    }

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
