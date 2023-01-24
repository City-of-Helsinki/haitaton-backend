package fi.hel.haitaton.hanke.domain;


enum class HaittojenHallintaKentta {
    PYORALIIKENTEEN_PAAREITIT,
    MERKITTAVAT_JOUKKOLIIKENNEREITIT,
    AUTOLIIKENTEEN_RUUHKAUTUMINEN,
    OMAN_JA_MUIDEN_HANKKEIDEN_KIERTOREITIT,
    MUUT_HANKKEET,
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
        fun pakolliset(): Set<HaittojenHallintaKentta> {
            return setOf(
                    PYORALIIKENTEEN_PAAREITIT,
                    MERKITTAVAT_JOUKKOLIIKENNEREITIT,
                    AUTOLIIKENTEEN_RUUHKAUTUMINEN,
                    OMAN_JA_MUIDEN_HANKKEIDEN_KIERTOREITIT,
                    MUUT_HANKKEET)
        }
    }
}

data class HaittojenHallintaKuvaus(
    val kaytossaHankkeessa: Boolean,
    val kuvaus: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HaittojenHallintaKuvaus

        if (kaytossaHankkeessa != other.kaytossaHankkeessa) return false
        if (kuvaus != other.kuvaus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kaytossaHankkeessa.hashCode()
        result = 31 * result + kuvaus.hashCode()
        return result
    }
}

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
