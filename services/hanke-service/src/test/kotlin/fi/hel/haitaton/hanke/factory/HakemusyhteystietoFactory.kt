package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import org.springframework.stereotype.Component

@Component
class HakemusyhteystietoFactory(
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository
) {

    companion object {
        const val DEFAULT_NIMI = "Oy Testi Ab"
        const val DEFAULT_SAHKOPOSTI = "hakija@testi.fi"
        const val DEFAULT_PUHELINNUMERO = "0401234567"
        const val DEFAULT_YTUNNUS = "1817548-2"

        fun createEntity(
            tyyppi: CustomerType = CustomerType.COMPANY,
            rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
            nimi: String = DEFAULT_NIMI,
            sahkoposti: String = DEFAULT_SAHKOPOSTI,
            puhelinnumero: String = DEFAULT_PUHELINNUMERO,
            ytunnus: String? = DEFAULT_YTUNNUS,
            application: ApplicationEntity,
        ): HakemusyhteystietoEntity =
            HakemusyhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                ytunnus = ytunnus,
                application = application
            )

        fun HakemusyhteystietoEntity.withYhteyshenkilo(
            hankekayttajaEntity: HankekayttajaEntity,
            tilaaja: Boolean = false
        ): HakemusyhteystietoEntity =
            this.apply {
                this.yhteyshenkilot =
                    listOf(
                        HakemusyhteyshenkiloFactory.createEntity(this, hankekayttajaEntity, tilaaja)
                    )
            }
    }

    fun saveHakemusyhteystieto(
        tyyppi: CustomerType = CustomerType.COMPANY,
        rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
        nimi: String = DEFAULT_NIMI,
        sahkoposti: String = DEFAULT_SAHKOPOSTI,
        puhelinnumero: String = DEFAULT_PUHELINNUMERO,
        ytunnus: String? = DEFAULT_YTUNNUS,
        application: ApplicationEntity
    ): HakemusyhteystietoEntity =
        hakemusyhteystietoRepository.save(
            createEntity(tyyppi, rooli, nimi, sahkoposti, puhelinnumero, ytunnus, application)
        )
}
