package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createCableReportApplicationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createExcavationNotificationArea
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.hakemus.InvoicingCustomer
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusEntityData
import fi.hel.haitaton.hanke.hakemus.PaperDecisionReceiver
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import java.security.InvalidParameterException
import java.time.ZonedDateTime

data class HakemusBuilder(
    private var hakemusEntity: HakemusEntity,
    private var hankeId: Int,
    private val userId: String,
    private val hakemusFactory: HakemusFactory,
    private val hakemusService: HakemusService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val hakemusRepository: HakemusRepository,
    private val hankeRepository: HankeRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    fun save(): Hakemus = hakemusService.getById(saveEntity().id)

    fun saveEntity(): HakemusEntity {
        val savedApplication = hakemusRepository.save(hakemusEntity)
        savedApplication.yhteystiedot.forEach { (_, yhteystieto) ->
            yhteystieto.yhteyshenkilot.forEach { yhteyshenkilo ->
                hakemusyhteyshenkiloRepository.save(yhteyshenkilo)
            }
        }
        return savedApplication
    }

    fun withStatus(
        status: ApplicationStatus? = ApplicationStatus.PENDING,
        alluId: Int? = 1,
        identifier: String? = defaultIdentifier(alluId),
    ): HakemusBuilder {
        hakemusEntity.apply {
            alluid = alluId
            alluStatus = status
            applicationIdentifier = identifier
        }
        return this
    }

    fun withNoAlluFields(): HakemusBuilder =
        withStatus(status = null, alluId = null, identifier = null)

    fun inHandling(alluId: Int = 1) = withStatus(ApplicationStatus.HANDLING, alluId)

    fun withName(name: String): HakemusBuilder =
        updateApplicationData({ copy(name = name) }, { copy(name = name) })

    fun withWorkDescription(workDescription: String): HakemusBuilder =
        updateApplicationData(
            { copy(workDescription = workDescription) },
            { copy(workDescription = workDescription) },
        )

    fun withArea(area: JohtoselvitysHakemusalue): HakemusBuilder {
        hakemusEntity.hakemusEntityData =
            (hakemusEntity.hakemusEntityData as JohtoselvityshakemusEntityData).let {
                it.copy(areas = (it.areas ?: listOf()).plus(area))
            }
        return this
    }

    fun withArea(area: KaivuilmoitusAlue): HakemusBuilder {
        hakemusEntity.hakemusEntityData =
            (hakemusEntity.hakemusEntityData as KaivuilmoitusEntityData).let {
                it.copy(areas = (it.areas ?: listOf()).plus(area))
            }
        return this
    }

    fun withArea(hankealueId: Int? = null) =
        updateApplicationData(
            { copy(areas = (areas ?: listOf()).plus(createCableReportApplicationArea())) },
            {
                copy(
                    areas =
                        (areas ?: listOf()).plus(
                            createExcavationNotificationArea(hankealueId = hankealueId ?: 0)
                        )
                )
            },
        )

    fun withStartTime(time: ZonedDateTime? = DateFactory.getStartDatetime()) =
        updateApplicationData({ copy(startTime = time) }, { copy(startTime = time) })

    fun withEndTime(time: ZonedDateTime? = DateFactory.getEndDatetime()) =
        updateApplicationData({ copy(endTime = time) }, { copy(endTime = time) })

    fun withoutCableReports() =
        updateApplicationData(
            { throw InvalidParameterException("Not available for cable reports.") },
            { copy(cableReportDone = false, cableReports = null) },
        )

    fun withCableReports(cableReports: List<String>) =
        updateApplicationData(
            { throw InvalidParameterException("Not available for cable reports.") },
            { copy(cableReportDone = true, cableReports = cableReports) },
        )

    fun withRockExcavation(rockExcavation: Boolean?) =
        updateApplicationData(
            { copy(rockExcavation = rockExcavation) },
            { copy(rockExcavation = rockExcavation) },
        )

    fun withWorkInvolves(
        constructionWork: Boolean = true,
        maintenanceWork: Boolean = false,
        emergencyWork: Boolean = false,
    ): HakemusBuilder =
        updateApplicationData(
            { throw InvalidParameterException("Not available for cable reports.") },
            {
                copy(
                    constructionWork = constructionWork,
                    maintenanceWork = maintenanceWork,
                    emergencyWork = emergencyWork,
                )
            },
        )

    fun withRequiredCompetence(requiredCompetence: Boolean = true): HakemusBuilder =
        updateApplicationData(
            { throw InvalidParameterException("Not available for cable reports.") },
            { copy(requiredCompetence = requiredCompetence) },
        )

    fun withInvoicingCustomer(
        invoicingCustomer: InvoicingCustomer? = ApplicationFactory.createCompanyInvoicingCustomer()
    ): HakemusBuilder =
        updateApplicationData(
            { throw InvalidParameterException("Not available for cable reports.") },
            { copy(invoicingCustomer = invoicingCustomer) },
        )

    fun withPaperReceiver(receiver: PaperDecisionReceiver = PaperDecisionReceiverFactory.default) =
        updateApplicationData(
            { copy(paperDecisionReceiver = receiver) },
            { copy(paperDecisionReceiver = receiver) },
        )

    private fun updateApplicationData(
        onCableReport: JohtoselvityshakemusEntityData.() -> JohtoselvityshakemusEntityData,
        onExcavationNotification: KaivuilmoitusEntityData.() -> KaivuilmoitusEntityData,
    ) = apply {
        hakemusEntity.hakemusEntityData =
            when (val data = hakemusEntity.hakemusEntityData) {
                is JohtoselvityshakemusEntityData -> {
                    data.onCableReport()
                }
                is KaivuilmoitusEntityData -> {
                    data.onExcavationNotification()
                }
            }
    }

    /** Set all the fields that need to be set for the application to be valid for sending. */
    fun withMandatoryFields(hankealue: SavedHankealue? = null): HakemusBuilder =
        when (hakemusEntity.hakemusEntityData) {
            is JohtoselvityshakemusEntityData ->
                withName(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                    .withWorkDescription(ApplicationFactory.DEFAULT_WORK_DESCRIPTION)
                    .withStartTime()
                    .withEndTime()
                    .withArea()
                    .withRockExcavation(false)
                    .hakija()
                    .tyonSuorittaja(founder())
            is KaivuilmoitusEntityData ->
                withName(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                    .withWorkDescription(ApplicationFactory.DEFAULT_WORK_DESCRIPTION)
                    .withStartTime()
                    .withEndTime()
                    .withArea(hankealue?.id)
                    .withCableReports(
                        listOf(ApplicationFactory.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                    )
                    .withWorkInvolves()
                    .withRequiredCompetence()
                    .hakija()
                    .tyonSuorittaja(founder())
                    .withInvoicingCustomer()
        }

    /**
     * Make the hakemus appear like it has been just sent. I.e. it has mandatory fields and allu
     * status fields filled.
     */
    fun asSent(
        status: ApplicationStatus? = ApplicationStatus.PENDING,
        alluId: Int? = 1,
        identifier: String? = defaultIdentifier(alluId),
        hankealue: SavedHankealue? = null,
    ) = this.withMandatoryFields(hankealue).withStatus(status, alluId, identifier)

    private fun founder(): HankekayttajaEntity =
        hankeKayttajaService.getKayttajaByUserId(hankeId, userId)!!

    fun hakija(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA)),
    ): HakemusBuilder = yhteystieto(ApplicationContactType.HAKIJA, yhteystieto, *yhteyshenkilot)

    fun hakija(kayttooikeustaso: Kayttooikeustaso, tilaaja: Boolean = true): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.HAKIJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA,
        )

    fun hakija(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): HakemusBuilder = hakija(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun tyonSuorittaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA)),
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
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): HakemusBuilder = tyonSuorittaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun rakennuttaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA)),
    ): HakemusBuilder =
        yhteystieto(ApplicationContactType.RAKENNUTTAJA, yhteystieto, *yhteyshenkilot)

    fun rakennuttaja(kayttooikeustaso: Kayttooikeustaso, tilaaja: Boolean = false): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.RAKENNUTTAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA,
        )

    fun rakennuttaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): HakemusBuilder = rakennuttaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun asianhoitaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA)),
    ): HakemusBuilder =
        yhteystieto(ApplicationContactType.ASIANHOITAJA, yhteystieto, *yhteyshenkilot)

    fun asianhoitaja(kayttooikeustaso: Kayttooikeustaso, tilaaja: Boolean = false): HakemusBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.ASIANHOITAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA,
        )

    fun asianhoitaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): HakemusBuilder = asianhoitaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    /** Creates each customer and saves the given hankekayttaja as contacts to all of them. */
    fun withEachCustomer(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): HakemusBuilder =
        hakija(first, *yhteyshenkilot)
            .tyonSuorittaja(first, *yhteyshenkilot)
            .rakennuttaja(first, *yhteyshenkilot)
            .asianhoitaja(first, *yhteyshenkilot)

    private fun yhteystieto(
        rooli: ApplicationContactType,
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): HakemusBuilder {
        val yhteystietoEntity = createYhteystietoEntity(rooli, yhteystieto)
        val yhteyshenkiloEntities =
            yhteyshenkilot.map { createYhteyshenkiloEntity(yhteystietoEntity, it) }
        yhteystietoEntity.yhteyshenkilot.addAll(yhteyshenkiloEntities)
        hakemusEntity.yhteystiedot[rooli] = yhteystietoEntity

        return this
    }

    private fun yhteystieto(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean,
        rooli: ApplicationContactType,
        kayttajaInput: HankekayttajaInput,
    ): HakemusBuilder {
        val yhteystietoEntity = createYhteystietoEntity(rooli, HakemusyhteystietoFactory.create())
        val kayttaja = kayttaja(kayttajaInput, kayttooikeustaso)
        val yhteyshenkiloEntity = createYhteyshenkiloEntity(yhteystietoEntity, kayttaja, tilaaja)

        yhteystietoEntity.yhteyshenkilot.add(yhteyshenkiloEntity)
        hakemusEntity.yhteystiedot[rooli] = yhteystietoEntity
        return this
    }

    private fun kayttaja(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankekayttajaEntity =
        hankeKayttajaFactory.findOrSaveIdentifiedUser(
            hakemusEntity.hanke.id,
            kayttajaInput,
            kayttooikeustaso = kayttooikeustaso,
        )

    private fun createYhteystietoEntity(
        rooli: ApplicationContactType,
        yhteystieto: Hakemusyhteystieto,
    ): HakemusyhteystietoEntity =
        HakemusyhteystietoEntity(
            tyyppi = yhteystieto.tyyppi,
            rooli = rooli,
            nimi = yhteystieto.nimi,
            sahkoposti = yhteystieto.sahkoposti,
            puhelinnumero = yhteystieto.puhelinnumero,
            registryKey = yhteystieto.registryKey,
            application = hakemusEntity,
        )

    private fun createYhteyshenkiloEntity(
        yhteystietoEntity: HakemusyhteystietoEntity,
        kayttaja: HankekayttajaEntity,
        tilaaja: Boolean = false,
    ) =
        HakemusyhteyshenkiloEntity(
            hankekayttaja = kayttaja,
            hakemusyhteystieto = yhteystietoEntity,
            tilaaja = tilaaja,
        )

    private fun defaultIdentifier(alluId: Int?) =
        when (hakemusEntity.applicationType) {
            ApplicationType.EXCAVATION_NOTIFICATION -> "KP23%05d".format(alluId)
            ApplicationType.CABLE_REPORT -> "JS23%05d".format(alluId)
        }
}
