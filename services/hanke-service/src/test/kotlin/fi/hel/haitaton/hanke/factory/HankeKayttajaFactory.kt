package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import java.util.UUID

object HankeKayttajaFactory {

    fun generateHankeKayttajat(amount: Int = 3): List<HankeKayttajaDto> =
        (1..amount).map {
            HankeKayttajaDto(
                id = UUID.randomUUID(),
                sahkoposti = "email.$it.address.com",
                nimi = "test name$it",
                kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                tunnistautunut = it % 2 == 0
            )
        }
}
