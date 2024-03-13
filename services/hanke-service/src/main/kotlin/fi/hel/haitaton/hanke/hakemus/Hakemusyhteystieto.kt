package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import java.util.UUID

data class Hakemusyhteystieto(
    val id: UUID,
    val tyyppi: CustomerType,
    val rooli: ApplicationContactType,
    val nimi: String,
    val sahkoposti: String,
    val puhelinnumero: String,
    val ytunnus: String?,
    val yhteyshenkilot: List<Hakemusyhteyshenkilo>,
) {
    fun toResponse(): CustomerWithContactsResponse =
        CustomerWithContactsResponse(
            customer =
                CustomerResponse(
                    yhteystietoId = id,
                    type = tyyppi,
                    name = nimi,
                    email = sahkoposti,
                    phone = puhelinnumero,
                    registryKey = ytunnus,
                ),
            contacts = yhteyshenkilot.map { it.toResponse() }
        )
}

data class Hakemusyhteyshenkilo(
    val id: UUID,
    val hankekayttajaId: UUID,
    val etunimi: String,
    val sukunimi: String,
    val sahkoposti: String,
    val puhelin: String,
    val tilaaja: Boolean,
) {
    fun toResponse(): ContactResponse =
        ContactResponse(hankekayttajaId, etunimi, sukunimi, sahkoposti, puhelin, tilaaja)
}
