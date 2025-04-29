package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
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
                },
        )

    fun toHakemusyhteystieto(hakemus: HakemusEntity): HakemusyhteystietoEntity {
        val hakemusyhteystieto =
            HakemusyhteystietoEntity(
                tyyppi = tyyppi,
                rooli = rooli,
                nimi = nimi,
                sahkoposti = sahkoposti,
                puhelinnumero = puhelinnumero,
                registryKey = registryKey,
                application = hakemus,
            )

        for (yhteyshenkilo in yhteyshenkilot) {
            hakemusyhteystieto.yhteyshenkilot.add(
                yhteyshenkilo.toHakemusYhteyshenkilo(hakemusyhteystieto)
            )
        }

        return hakemusyhteystieto
    }

    fun mergeToHakemusyhteystieto(hakemusyhteystieto: HakemusyhteystietoEntity) {
        hakemusyhteystieto.tyyppi = tyyppi
        hakemusyhteystieto.nimi = nimi
        hakemusyhteystieto.sahkoposti = sahkoposti
        hakemusyhteystieto.puhelinnumero = puhelinnumero
        hakemusyhteystieto.registryKey = registryKey

        val muutosilmoitusHankekayttajaIds = yhteyshenkilot.map { it.hankekayttaja.id }
        hakemusyhteystieto.yhteyshenkilot.removeAll {
            !muutosilmoitusHankekayttajaIds.contains(it.hankekayttaja.id)
        }

        for (yhteyshenkilo in yhteyshenkilot) {
            val hakemusyhteyshenkilo =
                hakemusyhteystieto.yhteyshenkilot.find {
                    it.hankekayttaja.id == yhteyshenkilo.hankekayttaja.id
                }
            if (hakemusyhteyshenkilo != null) {
                hakemusyhteyshenkilo.tilaaja = yhteyshenkilo.tilaaja
            } else {
                hakemusyhteystieto.yhteyshenkilot.add(
                    yhteyshenkilo.toHakemusYhteyshenkilo(hakemusyhteystieto)
                )
            }
        }
    }
}

interface YhteyshenkiloEntity {
    val id: UUID
    var hankekayttaja: HankekayttajaEntity
    var tilaaja: Boolean

    fun toHakemusYhteyshenkilo(yhteystieto: HakemusyhteystietoEntity): HakemusyhteyshenkiloEntity =
        HakemusyhteyshenkiloEntity(
            hakemusyhteystieto = yhteystieto,
            hankekayttaja = hankekayttaja,
            tilaaja = tilaaja,
        )
}
