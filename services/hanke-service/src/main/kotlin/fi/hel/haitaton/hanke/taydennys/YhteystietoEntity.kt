package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import java.util.UUID

interface YhteystietoEntity<H : YhteyshenkiloEntity> {
    val id: UUID
    var tyyppi: CustomerType
    val rooli: ApplicationContactType
    var nimi: String
    var sahkoposti: String
    var puhelinnumero: String
    var registryKey: String?

    val yhteyshenkilot: MutableList<H>

    fun toDomain() =
        Hakemusyhteystieto(
            id = id,
            tyyppi = tyyppi,
            rooli = rooli,
            nimi = nimi,
            sahkoposti = sahkoposti,
            puhelinnumero = puhelinnumero,
            registryKey = registryKey,
            yhteyshenkilot =
                yhteyshenkilot.map { yhteyshenkilo ->
                    Hakemusyhteyshenkilo(
                        id = id,
                        hankekayttajaId = yhteyshenkilo.hankekayttaja.id,
                        etunimi = yhteyshenkilo.hankekayttaja.etunimi,
                        sukunimi = yhteyshenkilo.hankekayttaja.sukunimi,
                        sahkoposti = yhteyshenkilo.hankekayttaja.sahkoposti,
                        puhelin = yhteyshenkilo.hankekayttaja.puhelin,
                        tilaaja = yhteyshenkilo.tilaaja,
                    )
                })
}
