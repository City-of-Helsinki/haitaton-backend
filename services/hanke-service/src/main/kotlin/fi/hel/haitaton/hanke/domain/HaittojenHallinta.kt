package fi.hel.haitaton.hanke.domain

import java.util.*

enum class HaittojenHallintaKentta(val pakollinen: Boolean = true) {
    PYORALIIKENTEEN_PAAREITIT(true),
    MERKITTAVAT_JOUKKOLIIKENNEREITIT(true),
    AUTOLIIKENTEEN_RUUHKAUTUMINEN(true),
    OMAN_JA_MUIDEN_HANKKEIDEN_KIERTOREITIT(true),
    MUUT_HANKKEET(true),
    MOOTTORI_LIIKENTEEN_VIIVYTYKSET,
    KISKOILLA_KULKEVAN_LIIKENTEEN_VIIVYTYKSET,
    SELKEA_ENNAKKO_OPASTUS_PAATOKSENTEKIJALLE,
    TURVALLINEN_KULKU,
    REITIT_EIVAT_PITENE,
    TOIMET_PAIVAMELULLE,
    TOIMET_TARINALLE,
    TOIMET_POLYLLE_JA_LIALLE,
    PILAANTUNEEN_MAAN_HALLINTA,
    YLEINEN_SIISTEYS_JA_KAUPUNKIKUVALLINEN_LAATU,
    RIITTAVAN_PYSAKOINTIPAIKKOJEN_VARMISTAMINEN,
    LIIKENNEVALOJEN_TOIMIVUUDEN_VARMISTAMINEN,
    ALUEVUOKRAUKSET_JA_MUUT_HANKKEET,
    PALVELU_JA_MYYNTIPISTEIDEN_NAKYVYYS,
    TOIMINTOJEN_SAAVUTETTAVUUS,
    SOSIAALISTEN_TOIMINTOJEN_SAILYTTAMINEN,
    SOSIAALINEN_TURVALLISUUS,
    VIHERALUEIDEN_SAILYMINEN,
    SUOJELTUJEN_KOHTEIDEN_SAILYMINEN,
    LINTUJEN_PESINTAAJANHUOMIOIMINEN,
    TOIMIEN_ENNAKKOTIEDOTTAMINEN;

    companion object {
        val pakolliset: Set<HaittojenHallintaKentta> by lazy {
            EnumSet.copyOf(values().filter { it.pakollinen })
        }
    }
}

data class HaittojenHallintaKuvaus(
    val kaytossaHankkeessa: Boolean,
    val kuvaus: String,
)

data class HaittojenHallinta(
    var kuvaukset: MutableMap<HaittojenHallintaKentta, HaittojenHallintaKuvaus> = mutableMapOf(),
) {
    fun put(field: HaittojenHallintaKentta, kuvaus: String) {
        kuvaukset[field] = HaittojenHallintaKuvaus(true, kuvaus)
    }

    fun remove(field: HaittojenHallintaKentta) {
        kuvaukset[field] = HaittojenHallintaKuvaus(false, "")
    }
}
