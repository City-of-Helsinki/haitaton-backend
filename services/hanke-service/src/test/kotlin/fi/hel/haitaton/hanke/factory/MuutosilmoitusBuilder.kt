package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.Hakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusEntityData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusEntityData
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteyshenkiloRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoituksenYhteystietoEntity
import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusEntity
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import java.security.InvalidParameterException
import java.time.OffsetDateTime
import java.time.ZonedDateTime

class MuutosilmoitusBuilder(
    private var muutosilmoitusEntity: MuutosilmoitusEntity,
    private var hankeId: Int,
    private val muutosilmoitusService: MuutosilmoitusService,
    private val muutosilmoitusRepository: MuutosilmoitusRepository,
    private val yhteyshenkiloRepository: MuutosilmoituksenYhteyshenkiloRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {

    fun save(): Muutosilmoitus = muutosilmoitusService.find(saveEntity().hakemusId)!!

    fun saveEntity(): MuutosilmoitusEntity {
        val savedMuutosilmoitus = muutosilmoitusRepository.save(muutosilmoitusEntity)
        savedMuutosilmoitus.yhteystiedot.forEach { (_, yhteystieto) ->
            yhteystieto.yhteyshenkilot.forEach { yhteyshenkilo ->
                yhteyshenkiloRepository.save(yhteyshenkilo)
            }
        }
        return savedMuutosilmoitus
    }

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

    fun withStartTime(startTime: ZonedDateTime?) =
        updateApplicationData({ copy(startTime = startTime) }, { copy(startTime = startTime) })

    fun withEndTime(endTime: ZonedDateTime?) =
        updateApplicationData({ copy(endTime = endTime) }, { copy(endTime = endTime) })

    fun withConstructionWork(constructionWork: Boolean) =
        updateApplicationData(
            { copy(constructionWork = constructionWork) },
            { copy(constructionWork = constructionWork) },
        )

    fun withMaintenanceWork(maintenanceWork: Boolean) =
        updateApplicationData(
            { copy(maintenanceWork = maintenanceWork) },
            { copy(maintenanceWork = maintenanceWork) },
        )

    fun withCustomerReference(customerReference: String) =
        updateApplicationData(
            { invalidHakemusType() },
            { copy(customerReference = customerReference) },
        )

    fun withWorkDescription(description: String) =
        updateApplicationData(
            { copy(workDescription = description) },
            { copy(workDescription = description) },
        )

    fun withAdditionalInfo(info: String) =
        updateApplicationData({ invalidHakemusType() }, { copy(additionalInfo = info) })

    fun withSent(
        sent: OffsetDateTime? = MuutosilmoitusFactory.DEFAULT_SENT
    ): MuutosilmoitusBuilder {
        muutosilmoitusEntity.sent = sent
        return this
    }

    fun withoutYhteystiedot(): MuutosilmoitusBuilder {
        muutosilmoitusEntity.yhteystiedot = mutableMapOf()
        return this
    }

    fun hakija(
        yhteystieto: MuutosilmoituksenYhteystietoEntity =
            MuutosilmoitusFactory.createYhteystietoEntity(
                muutosilmoitusEntity,
                rooli = ApplicationContactType.HAKIJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA)),
    ): MuutosilmoitusBuilder =
        yhteystieto(ApplicationContactType.HAKIJA, yhteystieto, *yhteyshenkilot)

    fun rakennuttaja(
        yhteystieto: MuutosilmoituksenYhteystietoEntity =
            MuutosilmoitusFactory.createYhteystietoEntity(
                muutosilmoitusEntity,
                rooli = ApplicationContactType.RAKENNUTTAJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA)),
    ): MuutosilmoitusBuilder =
        yhteystieto(ApplicationContactType.RAKENNUTTAJA, yhteystieto, *yhteyshenkilot)

    fun tyonSuorittaja(
        yhteystieto: MuutosilmoituksenYhteystietoEntity =
            MuutosilmoitusFactory.createYhteystietoEntity(
                muutosilmoitusEntity,
                rooli = ApplicationContactType.TYON_SUORITTAJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA)),
    ): MuutosilmoitusBuilder =
        yhteystieto(ApplicationContactType.TYON_SUORITTAJA, yhteystieto, *yhteyshenkilot)

    fun asianhoitaja(
        yhteystieto: MuutosilmoituksenYhteystietoEntity =
            MuutosilmoitusFactory.createYhteystietoEntity(
                muutosilmoitusEntity,
                rooli = ApplicationContactType.ASIANHOITAJA,
            ),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA)),
    ): MuutosilmoitusBuilder =
        yhteystieto(ApplicationContactType.ASIANHOITAJA, yhteystieto, *yhteyshenkilot)

    private fun yhteystieto(
        rooli: ApplicationContactType,
        yhteystietoEntity: MuutosilmoituksenYhteystietoEntity =
            MuutosilmoitusFactory.createYhteystietoEntity(muutosilmoitusEntity),
        vararg yhteyshenkilot: HankekayttajaEntity,
    ): MuutosilmoitusBuilder {
        val yhteyshenkiloEntities =
            yhteyshenkilot.map { createYhteyshenkiloEntity(yhteystietoEntity, it) }
        yhteystietoEntity.yhteyshenkilot.addAll(yhteyshenkiloEntities)
        muutosilmoitusEntity.yhteystiedot[rooli] = yhteystietoEntity

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
        yhteystietoEntity: MuutosilmoituksenYhteystietoEntity,
        kayttaja: HankekayttajaEntity,
        tilaaja: Boolean = false,
    ) =
        MuutosilmoituksenYhteyshenkiloEntity(
            hankekayttaja = kayttaja,
            yhteystieto = yhteystietoEntity,
            tilaaja = tilaaja,
        )

    private fun updateApplicationData(
        onCableReport: JohtoselvityshakemusEntityData.() -> JohtoselvityshakemusEntityData,
        onExcavationNotification: KaivuilmoitusEntityData.() -> KaivuilmoitusEntityData,
    ): MuutosilmoitusBuilder = apply {
        muutosilmoitusEntity.hakemusData =
            when (val data = muutosilmoitusEntity.hakemusData) {
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
