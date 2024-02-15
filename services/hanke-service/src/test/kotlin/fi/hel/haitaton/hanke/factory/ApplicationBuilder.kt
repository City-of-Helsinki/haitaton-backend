package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.profiili.Names
import fi.hel.haitaton.hanke.profiili.ProfiiliClient

data class ApplicationBuilder(
    private val application: Application,
    private val userId: String,
    private val names: Names = ProfiiliFactory.DEFAULT_NAMES,
    private val applicationService: ApplicationService,
    private val applicationRepository: ApplicationRepository,
    private val mockProfiiliClient: ProfiiliClient,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    /**
     * Create this application and then update it to give it fuller information. This method does an
     * actual update, so it will set modifiedBy and modifiedAt columns and bump version up to 1.
     */
    fun save(): Application = applicationService.create(application, userId)

    /** Save the entity with [save], and - for convenience - get the saved entity from DB. */
    private fun saveEntity(): ApplicationEntity =
        applicationRepository.getReferenceById(save().id!!)

    fun saveWithYhteystiedot(f: HakemusyhteystietoBuilder.() -> Unit): ApplicationEntity {
        val entity = saveEntity()
        val builder =
            HakemusyhteystietoBuilder(
                entity,
                userId,
                hankeKayttajaFactory,
                hakemusyhteystietoRepository,
                hakemusyhteyshenkiloRepository,
            )
        builder.f()
        return entity
    }
}

data class HakemusyhteystietoBuilder(
    private val applicationEntity: ApplicationEntity,
    private val userId: String,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    fun hakija(
        hakija: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(hakija, kayttooikeustaso, true),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.HAKIJA))
        return this
    }

    fun tyonSuorittaja(
        tyonSuorittaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(tyonSuorittaja, kayttooikeustaso),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.TYON_SUORITTAJA))
        return this
    }

    fun rakennuttaja(
        rakennuttaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(rakennuttaja, kayttooikeustaso),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.RAKENNUTTAJA))
        return this
    }

    fun asianhoitaja(
        asianhoitaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(asianhoitaja, kayttooikeustaso),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.ASIANHOITAJA))
        return this
    }

    private fun addYhteyshenkilo(
        yhteystietoEntity: HakemusyhteystietoEntity,
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        tilaaja: Boolean = false
    ) {
        val kayttaja =
            hankeKayttajaFactory.findOrSaveIdentifiedUser(
                applicationEntity.hanke.id,
                kayttajaInput,
                kayttooikeustaso
            )
        addYhteyshenkilo(yhteystietoEntity, kayttaja, tilaaja)
    }

    private fun addYhteyshenkilo(
        yhteystietoEntity: HakemusyhteystietoEntity,
        kayttaja: HankekayttajaEntity,
        tilaaja: Boolean = false
    ) {
        hakemusyhteyshenkiloRepository.save(
            HakemusyhteyshenkiloEntity(
                hankekayttaja = kayttaja,
                hakemusyhteystieto = yhteystietoEntity,
                tilaaja = tilaaja
            )
        )
    }

    private fun saveYhteystieto(rooli: ApplicationContactType): HakemusyhteystietoEntity {
        return hakemusyhteystietoRepository.save(
            HakemusyhteystietoFactory.createEntity(rooli = rooli, application = applicationEntity)
        )
    }

    private fun defaultYhteyshenkilo(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false
    ): (HakemusyhteystietoEntity) -> Unit = { yhteystieto ->
        addYhteyshenkilo(yhteystieto, kayttajaInput, kayttooikeustaso, tilaaja)
    }
}
