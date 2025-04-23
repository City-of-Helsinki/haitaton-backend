package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusEntityData
import fi.hel.haitaton.hanke.hakemus.StreetAddress
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.taydennys.Taydennys
import fi.hel.haitaton.hanke.taydennys.TaydennysEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennysService
import fi.hel.haitaton.hanke.taydennys.TaydennysyhteyshenkiloEntity
import fi.hel.haitaton.hanke.taydennys.TaydennysyhteyshenkiloRepository
import fi.hel.haitaton.hanke.taydennys.TaydennysyhteystietoEntity
import java.security.InvalidParameterException
import java.time.ZonedDateTime

data class TaydennysBuilder(
    private var taydennysEntity: TaydennysEntity,
    private var hankeId: Int,
    private val taydennysService: TaydennysService,
    private val taydennysRepository: TaydennysRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val taydennysyhteyshenkiloRepository: TaydennysyhteyshenkiloRepository,
) {
    fun save(): Taydennys = taydennysService.findTaydennys(saveEntity().hakemusId())!!

    fun saveEntity(): TaydennysEntity {
        val savedTaydennys = taydennysRepository.save(taydennysEntity)
        savedTaydennys.yhteystiedot.forEach { (_, yhteystieto) ->
            yhteystieto.yhteyshenkilot.forEach { yhteyshenkilo ->
                taydennysyhteyshenkiloRepository.save(yhteyshenkilo)
            }
        }
        return savedTaydennys
    }

    fun withName(name: String): TaydennysBuilder =
        updateApplicationData({ copy(name = name) }, { copy(name = name) })

    fun withConstructionWork(constructionWork: Boolean): TaydennysBuilder =
        updateApplicationData(
            { copy(constructionWork = constructionWork) },
            { copy(constructionWork = constructionWork) },
        )

    fun withMaintenanceWork(maintenanceWork: Boolean): TaydennysBuilder =
        updateApplicationData(
            { copy(maintenanceWork = maintenanceWork) },
            { copy(maintenanceWork = maintenanceWork) },
        )

    fun withEmergencyWork(emergencyWork: Boolean): TaydennysBuilder =
        updateApplicationData(
            { copy(emergencyWork = emergencyWork) },
            { copy(emergencyWork = emergencyWork) },
        )

    fun withCableReports(identifiers: List<String>): TaydennysBuilder =
        updateApplicationData({ invalidHakemusType() }, { copy(cableReports = identifiers) })

    fun withPlacementContracts(identifiers: List<String>): TaydennysBuilder =
        updateApplicationData({ invalidHakemusType() }, { copy(placementContracts = identifiers) })

    fun withWorkDescription(description: String): TaydennysBuilder =
        updateApplicationData(
            { copy(workDescription = description) },
            { copy(workDescription = description) },
        )

    fun withStreetAddress(address: String): TaydennysBuilder =
        updateApplicationData(
            { copy(postalAddress = postalAddress?.copy(streetAddress = StreetAddress(address))) },
            { invalidHakemusType() },
        )

    fun withStartTime(startTime: ZonedDateTime?): TaydennysBuilder =
        updateApplicationData({ copy(startTime = startTime) }, { copy(startTime = startTime) })

    fun withEndTime(endTime: ZonedDateTime?): TaydennysBuilder =
        updateApplicationData({ copy(endTime = endTime) }, { copy(endTime = endTime) })

    fun withAreas(areas: List<Hakemusalue>) =
        updateApplicationData(
            {
                if (areas.any { it !is JohtoselvitysHakemusalue }) throw InvalidParameterException()
                copy(areas = areas.filterIsInstance<JohtoselvitysHakemusalue>())
            },
            {
                if (areas.any { it !is KaivuilmoitusAlue }) throw InvalidParameterException()
                copy(areas = areas.filterIsInstance<KaivuilmoitusAlue>())
            },
        )

    fun hakija(
        yhteystieto: TaydennysyhteystietoEntity =
            TaydennysFactory.createYhteystietoEntity(
                taydennysEntity,
                rooli = ApplicationContactType.HAKIJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA)),
    ): TaydennysBuilder = yhteystieto(ApplicationContactType.HAKIJA, yhteystieto, *yhteyshenkilot)

    fun hakija(kayttooikeustaso: Kayttooikeustaso, tilaaja: Boolean = true): TaydennysBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.HAKIJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA,
        )

    fun hakija(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): TaydennysBuilder = hakija(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun withoutYhteystiedot(): TaydennysBuilder {
        taydennysEntity.yhteystiedot = mutableMapOf()
        return this
    }

    fun tyonSuorittaja(
        yhteystieto: TaydennysyhteystietoEntity =
            TaydennysFactory.createYhteystietoEntity(
                taydennysEntity,
                rooli = ApplicationContactType.TYON_SUORITTAJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA)),
    ): TaydennysBuilder =
        yhteystieto(ApplicationContactType.TYON_SUORITTAJA, yhteystieto, *yhteyshenkilot)

    fun tyonSuorittaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): TaydennysBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.TYON_SUORITTAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA,
        )

    fun tyonSuorittaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): TaydennysBuilder = tyonSuorittaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun rakennuttaja(
        yhteystieto: TaydennysyhteystietoEntity =
            TaydennysFactory.createYhteystietoEntity(
                taydennysEntity,
                rooli = ApplicationContactType.RAKENNUTTAJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA)),
    ): TaydennysBuilder =
        yhteystieto(ApplicationContactType.RAKENNUTTAJA, yhteystieto, *yhteyshenkilot)

    fun rakennuttaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): TaydennysBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.RAKENNUTTAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA,
        )

    fun rakennuttaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): TaydennysBuilder = rakennuttaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun asianhoitaja(
        yhteystieto: TaydennysyhteystietoEntity =
            TaydennysFactory.createYhteystietoEntity(
                taydennysEntity,
                rooli = ApplicationContactType.ASIANHOITAJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA)),
    ): TaydennysBuilder =
        yhteystieto(ApplicationContactType.ASIANHOITAJA, yhteystieto, *yhteyshenkilot)

    fun asianhoitaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): TaydennysBuilder =
        yhteystieto(
            kayttooikeustaso,
            tilaaja,
            ApplicationContactType.ASIANHOITAJA,
            HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA,
        )

    fun asianhoitaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): TaydennysBuilder = asianhoitaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    private fun yhteystieto(
        rooli: ApplicationContactType,
        yhteystietoEntity: TaydennysyhteystietoEntity =
            TaydennysFactory.createYhteystietoEntity(taydennysEntity),
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): TaydennysBuilder {
        val yhteyshenkiloEntities =
            yhteyshenkilot.map { createYhteyshenkiloEntity(yhteystietoEntity, it) }
        yhteystietoEntity.yhteyshenkilot.addAll(yhteyshenkiloEntities)
        taydennysEntity.yhteystiedot[rooli] = yhteystietoEntity

        return this
    }

    private fun yhteystieto(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean,
        rooli: ApplicationContactType,
        kayttajaInput: HankekayttajaInput,
    ): TaydennysBuilder {
        val yhteystietoEntity =
            TaydennysFactory.createYhteystietoEntity(taydennysEntity, rooli = rooli)
        val kayttaja = kayttaja(kayttajaInput, kayttooikeustaso)
        val yhteyshenkiloEntity = createYhteyshenkiloEntity(yhteystietoEntity, kayttaja, tilaaja)

        yhteystietoEntity.yhteyshenkilot.add(yhteyshenkiloEntity)
        taydennysEntity.yhteystiedot[rooli] = yhteystietoEntity
        return this
    }

    private fun kayttaja(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankekayttajaEntity =
        hankeKayttajaFactory.findOrSaveIdentifiedUser(
            hankeId,
            kayttajaInput,
            kayttooikeustaso = kayttooikeustaso,
        )

    private fun createYhteyshenkiloEntity(
        yhteystietoEntity: TaydennysyhteystietoEntity,
        kayttaja: HankekayttajaEntity,
        tilaaja: Boolean = false,
    ) =
        TaydennysyhteyshenkiloEntity(
            hankekayttaja = kayttaja,
            taydennysyhteystieto = yhteystietoEntity,
            tilaaja = tilaaja,
        )

    private fun updateApplicationData(
        onCableReport: JohtoselvityshakemusEntityData.() -> JohtoselvityshakemusEntityData,
        onExcavationNotification: KaivuilmoitusEntityData.() -> KaivuilmoitusEntityData,
    ): TaydennysBuilder = apply {
        taydennysEntity.hakemusData =
            when (val data = taydennysEntity.hakemusData) {
                is JohtoselvityshakemusEntityData -> {
                    data.onCableReport()
                }
                is KaivuilmoitusEntityData -> {
                    data.onExcavationNotification()
                }
            }
    }

    private fun HakemusEntityData.invalidHakemusType(): Nothing {
        throw InvalidParameterException("Not available for hakemus type $applicationType.")
    }
}
