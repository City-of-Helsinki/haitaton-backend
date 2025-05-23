package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoEntity
import fi.hel.haitaton.hanke.hakemus.Laskutusyhteystieto
import java.util.UUID
import org.springframework.stereotype.Component

@Component
object HakemusyhteystietoFactory {

    private val DEFAULT_ID = UUID.fromString("af23b9e5-208e-40ef-9291-962c05d783df")
    const val DEFAULT_NIMI = "Oy Testi Ab"
    const val DEFAULT_SAHKOPOSTI = "hakija@testi.fi"
    const val DEFAULT_PUHELINNUMERO = "0401234567"
    const val DEFAULT_YTUNNUS = "1817548-2"
    private const val DEFAULT_OVT = "003718175482"

    const val DEFAULT_PERSON_NIMI = "Pertti Perushenkilö"
    const val DEFAULT_PERSON_SAHKOPOSTI = "pertti@perus.fi"
    const val DEFAULT_PERSON_PUHELINNUMERO = "554466546"

    fun createEntity(
        tyyppi: CustomerType = CustomerType.COMPANY,
        rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
        nimi: String = DEFAULT_NIMI,
        sahkoposti: String = DEFAULT_SAHKOPOSTI,
        puhelinnumero: String = DEFAULT_PUHELINNUMERO,
        ytunnus: String? = DEFAULT_YTUNNUS,
        application: HakemusEntity,
    ): HakemusyhteystietoEntity =
        HakemusyhteystietoEntity(
            tyyppi = tyyppi,
            rooli = rooli,
            nimi = nimi,
            sahkoposti = sahkoposti,
            puhelinnumero = puhelinnumero,
            registryKey = ytunnus,
            application = application,
        )

    fun create(
        id: UUID = DEFAULT_ID,
        tyyppi: CustomerType = CustomerType.COMPANY,
        rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
        nimi: String = DEFAULT_NIMI,
        sahkoposti: String = DEFAULT_SAHKOPOSTI,
        puhelinnumero: String = DEFAULT_PUHELINNUMERO,
        registryKey: String? = DEFAULT_YTUNNUS,
        yhteyshenkilot: List<Hakemusyhteyshenkilo> = listOf(),
    ) =
        Hakemusyhteystieto(
            id = id,
            tyyppi = tyyppi,
            rooli = rooli,
            nimi = nimi,
            sahkoposti = sahkoposti,
            puhelinnumero = puhelinnumero,
            registryKey = registryKey,
            yhteyshenkilot = yhteyshenkilot,
        )

    fun createPerson(
        id: UUID = DEFAULT_ID,
        tyyppi: CustomerType = CustomerType.PERSON,
        rooli: ApplicationContactType = ApplicationContactType.HAKIJA,
        nimi: String = DEFAULT_PERSON_NIMI,
        sahkoposti: String = DEFAULT_PERSON_SAHKOPOSTI,
        puhelinnumero: String = DEFAULT_PERSON_PUHELINNUMERO,
        registryKey: String? = null,
        yhteyshenkilot: List<Hakemusyhteyshenkilo> = listOf(),
    ) =
        Hakemusyhteystieto(
            id,
            tyyppi,
            rooli,
            nimi,
            sahkoposti,
            puhelinnumero,
            registryKey,
            yhteyshenkilot,
        )

    fun Hakemusyhteystieto.withYhteyshenkilo(
        etunimi: String = HakemusyhteyshenkiloFactory.DEFAULT_ETUNIMI,
        sukunimi: String = HakemusyhteyshenkiloFactory.DEFAULT_SUKUNIMI,
        sahkoposti: String = HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI,
        puhelin: String = HakemusyhteyshenkiloFactory.DEFAULT_PUHELIN,
        tilaaja: Boolean = HakemusyhteyshenkiloFactory.DEFAULT_TILAAJA,
    ): Hakemusyhteystieto {
        val yhteyshenkilo =
            HakemusyhteyshenkiloFactory.create(
                etunimi = etunimi,
                sukunimi = sukunimi,
                sahkoposti = sahkoposti,
                puhelin = puhelin,
                tilaaja = tilaaja,
            )
        return copy(yhteyshenkilot = yhteyshenkilot + yhteyshenkilo)
    }

    fun createLaskutusyhteystieto(
        tyyppi: CustomerType = CustomerType.COMPANY,
        nimi: String = DEFAULT_NIMI,
        sahkoposti: String? = null,
        puhelinnumero: String? = null,
        registryKey: String? = DEFAULT_YTUNNUS,
        ovttunnus: String? = DEFAULT_OVT,
        valittajanTunnus: String? = DEFAULT_OVT,
        asiakkaanViite: String? = null,
        katuosoite: String? = null,
        postinumero: String? = null,
        postitoimipaikka: String? = null,
    ) =
        Laskutusyhteystieto(
            tyyppi = tyyppi,
            nimi = nimi,
            registryKey = registryKey,
            ovttunnus = ovttunnus,
            valittajanTunnus = valittajanTunnus,
            asiakkaanViite = asiakkaanViite,
            katuosoite = katuosoite,
            postinumero = postinumero,
            postitoimipaikka = postitoimipaikka,
            sahkoposti = sahkoposti,
            puhelinnumero = puhelinnumero,
        )
}
