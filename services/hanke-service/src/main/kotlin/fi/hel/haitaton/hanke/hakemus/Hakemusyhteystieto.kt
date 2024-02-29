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
    var yhteyshenkilot: List<Hakemusyhteyshenkilo> = emptyList(),
)

data class Hakemusyhteyshenkilo(
    val hankekayttajaId: UUID,
    val etunimi: String,
    val sukunimi: String,
    val sahkoposti: String,
    val puhelin: String,
    val tilaaja: Boolean,
)
