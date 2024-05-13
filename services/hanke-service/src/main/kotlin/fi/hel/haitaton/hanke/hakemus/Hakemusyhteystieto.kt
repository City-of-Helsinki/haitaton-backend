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

    fun kokoNimi() = "$etunimi $sukunimi".trim()
}

data class Laskutusyhteystieto(
    val tyyppi: CustomerType,
    val nimi: String,
    val ytunnus: String?,
    val ovttunnus: String?,
    val valittajanTunnus: String?,
    val asiakkaanViite: String?,
    val katuosoite: String?,
    val postinumero: String?,
    val postitoimipaikka: String?,
    val sahkoposti: String?,
    val puhelinnumero: String?,
) {
    fun toResponse(): InvoicingCustomerResponse =
        InvoicingCustomerResponse(
            tyyppi,
            nimi,
            ytunnus,
            ovttunnus,
            valittajanTunnus,
            asiakkaanViite,
            PostalAddress(StreetAddress(katuosoite), postinumero ?: "", postitoimipaikka ?: ""),
            sahkoposti,
            puhelinnumero
        )
}
