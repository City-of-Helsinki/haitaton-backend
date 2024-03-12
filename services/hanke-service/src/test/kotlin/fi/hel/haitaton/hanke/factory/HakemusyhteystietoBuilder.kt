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

    fun kayttaja(
        kayttajaInput: HankekayttajaInput,
        kayttooikeustaso: Kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
    ): HankekayttajaEntity =
        hankeKayttajaFactory.findOrSaveIdentifiedUser(
            applicationEntity.hanke.id,
            kayttajaInput,
            kayttooikeustaso = kayttooikeustaso,
        )

    fun hakija(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA))
    ): HakemusyhteystietoEntity {
        return saveYhteystieto(ApplicationContactType.HAKIJA, yhteystieto, yhteyshenkilot.asList())
    }

    fun hakija(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = true,
    ): HakemusyhteystietoEntity {
        val kayttaja = kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA, kayttooikeustaso)
        return saveYhteystieto(ApplicationContactType.HAKIJA, kayttaja, tilaaja)
    }

    fun hakija(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusyhteystietoEntity = hakija(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun tyonSuorittaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA))
    ): HakemusyhteystietoEntity {
        return saveYhteystieto(
            ApplicationContactType.TYON_SUORITTAJA,
            yhteystieto,
            yhteyshenkilot.asList()
        )
    }

    fun tyonSuorittaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): HakemusyhteystietoEntity {
        val kayttaja = kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA, kayttooikeustaso)
        return saveYhteystieto(ApplicationContactType.TYON_SUORITTAJA, kayttaja, tilaaja)
    }

    fun tyonSuorittaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusyhteystietoEntity = tyonSuorittaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun rakennuttaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA))
    ): HakemusyhteystietoEntity {
        return saveYhteystieto(
            ApplicationContactType.RAKENNUTTAJA,
            yhteystieto,
            yhteyshenkilot.asList()
        )
    }

    fun rakennuttaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): HakemusyhteystietoEntity {
        val kayttaja = kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA, kayttooikeustaso)
        return saveYhteystieto(ApplicationContactType.RAKENNUTTAJA, kayttaja, tilaaja)
    }

    fun rakennuttaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusyhteystietoEntity = rakennuttaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

    fun asianhoitaja(
        yhteystieto: Hakemusyhteystieto = HakemusyhteystietoFactory.create(),
        vararg yhteyshenkilot: HankekayttajaEntity =
            arrayOf(kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA))
    ): HakemusyhteystietoEntity {
        return saveYhteystieto(
            ApplicationContactType.ASIANHOITAJA,
            yhteystieto,
            yhteyshenkilot.asList()
        )
    }

    fun asianhoitaja(
        kayttooikeustaso: Kayttooikeustaso,
        tilaaja: Boolean = false,
    ): HakemusyhteystietoEntity {
        val kayttaja = kayttaja(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA, kayttooikeustaso)
        return saveYhteystieto(ApplicationContactType.ASIANHOITAJA, kayttaja, tilaaja)
    }

    fun asianhoitaja(
        first: HankekayttajaEntity,
        vararg yhteyshenkilot: HankekayttajaEntity
    ): HakemusyhteystietoEntity = asianhoitaja(yhteyshenkilot = arrayOf(first) + yhteyshenkilot)

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

    private fun createEntity(
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

    private fun saveYhteystieto(
        rooli: ApplicationContactType,
        yhteystieto: Hakemusyhteystieto,
        yhteyshenkilot: Iterable<HankekayttajaEntity>,
    ): HakemusyhteystietoEntity {
        val entity = createEntity(rooli, yhteystieto)
        val saved = hakemusyhteystietoRepository.save(entity)
        yhteyshenkilot.forEach { kayttaja -> addYhteyshenkilo(saved, kayttaja) }
        return saved
    }

    private fun saveYhteystieto(
        rooli: ApplicationContactType,
        yhteyshenkilo: HankekayttajaEntity,
        tilaaja: Boolean,
    ): HakemusyhteystietoEntity {
        val entity = createEntity(rooli, HakemusyhteystietoFactory.create())
        val saved = hakemusyhteystietoRepository.save(entity)
        addYhteyshenkilo(saved, yhteyshenkilo, tilaaja)
        return saved
    }
}
