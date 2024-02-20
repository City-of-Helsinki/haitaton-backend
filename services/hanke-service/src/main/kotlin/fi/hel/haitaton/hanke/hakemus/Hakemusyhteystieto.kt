package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import java.util.UUID

data class Hakemusyhteystieto(
    val id: UUID,
    val tyyppi: CustomerType,
    val rooli: ApplicationContactType,
    var nimi: String,
    var sahkoposti: String?,
    var puhelinnumero: String?,
    var ytunnus: String?,
)
