package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import java.util.UUID
import org.springframework.stereotype.Component

@Component
object HakemusyhteystietoFactory {

    fun createEntity(
        id: UUID = UUID.randomUUID(),
        tyyppi: CustomerType = CustomerType.COMPANY,
        rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
        nimi: String = "Oy Testi Ab",
        sahkoposti: String = "hakija@testi.fi",
        puhelinnumero: String = "0401234567",
        ytunnus: String? = "1817548-2",
        application: ApplicationEntity,
    ): HakemusyhteystietoEntity =
        HakemusyhteystietoEntity(
                id,
                tyyppi,
                rooli,
                nimi,
                sahkoposti,
                puhelinnumero,
                ytunnus,
                application,
            )
            .apply { application.yhteystiedot[rooli] = this }

    fun HakemusyhteystietoEntity.withYhteyshenkilo(
        hankekayttajaEntity: HankekayttajaEntity,
        tilaaja: Boolean = false
    ): HakemusyhteystietoEntity =
        this.apply {
            this.yhteyshenkilot =
                listOf(HakemusyhteyshenkiloFactory.createEntity(this, hankekayttajaEntity, tilaaja))
        }
}
