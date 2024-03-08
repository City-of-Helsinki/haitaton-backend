package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso

data class HakemusyhteystietoBuilder(
    private val applicationEntity: ApplicationEntity,
    private val userId: String,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
) {
    fun kayttaja(
        sahkoposti: String = HankeKayttajaFactory.KAKE_EMAIL,
        userId: String = HankeKayttajaFactory.FAKE_USERID
    ): HankekayttajaEntity =
        hankeKayttajaFactory.saveIdentifiedUser(
            applicationEntity.hanke.id,
            sahkoposti = sahkoposti,
            userId = userId
        )

    fun hakija(
        hakija: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(hakija, kayttooikeustaso, true),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.HAKIJA, yhteystieto))
        return this
    }

    fun tyonSuorittaja(
        tyonSuorittaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(tyonSuorittaja, kayttooikeustaso),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.TYON_SUORITTAJA, yhteystieto))
        return this
    }

    fun rakennuttaja(
        rakennuttaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(rakennuttaja, kayttooikeustaso),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.RAKENNUTTAJA, yhteystieto))
        return this
    }

    fun asianhoitaja(
        asianhoitaja: HankekayttajaInput = HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        f: (HakemusyhteystietoEntity) -> Unit =
            defaultYhteyshenkilo(asianhoitaja, kayttooikeustaso),
    ): HakemusyhteystietoBuilder {
        f(saveYhteystieto(ApplicationContactType.ASIANHOITAJA, yhteystieto))
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

    fun addYhteyshenkilo(
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

    private fun saveYhteystieto(
        rooli: ApplicationContactType,
        yhteystieto: Hakemusyhteystieto
    ): HakemusyhteystietoEntity {
        val entity =
            HakemusyhteystietoEntity(
                tyyppi = yhteystieto.tyyppi,
                rooli = rooli,
                nimi = yhteystieto.nimi,
                sahkoposti = yhteystieto.sahkoposti,
                puhelinnumero = yhteystieto.puhelinnumero,
                ytunnus = yhteystieto.ytunnus,
                application = applicationEntity,
            )
        return hakemusyhteystietoRepository.save(entity)
    }

    private fun defaultYhteyshenkilo(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false
    ): (HakemusyhteystietoEntity) -> Unit = { yhteystieto ->
        addYhteyshenkilo(yhteystieto, kayttajaInput, kayttooikeustaso, tilaaja)
    }
}
