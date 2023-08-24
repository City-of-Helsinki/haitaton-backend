package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.permissions.Role
import java.util.UUID

object HankeKayttajaFactory {

    fun generateHankeKayttajat(amount: Int = 3): List<HankeKayttajaDto> =
        (1..amount).map {
            HankeKayttajaDto(
                id = UUID.randomUUID(),
                sahkoposti = "email.$it.address.com",
                nimi = "test name$it",
                rooli = Role.KATSELUOIKEUS,
                tunnistautunut = it % 2 == 0
            )
        }
}
