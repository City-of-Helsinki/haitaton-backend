package fi.hel.haitaton.hanke.tormaystarkastelu

enum class LuokitteluType {
    HAITTA_AJAN_KESTO,
    VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN,
    AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS,
    KATULUOKKA,
    AUTOLIIKENTEEN_MAARA
}

interface Luokittelu {
    val value: Int
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class VaikutusAutoliikenteenKaistamaariin(override val value: Int) : Luokittelu {
    EI_VAIKUTA(1),
    VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA(2),
    VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA(3),
    VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA(4),
    VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA(5)
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class AutoliikenteenKaistavaikutustenPituus(override val value: Int) : Luokittelu {
    EI_VAIKUTA_KAISTAJARJESTELYIHIN(1),
    ALLE_10_METRIA(2),
    _10_99_METRIA(3),
    _100_499_METRIA(4),
    _500_METRIA_TAI_ENEMMAN(5)
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Meluhaitta {
    SATUNNAINEN_HAITTA,
    LYHYTAIKAINEN_TOISTUVA_HAITTA,
    PITKAKESTOINEN_TOISTUVA_HAITTA
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Polyhaitta {
    SATUNNAINEN_HAITTA,
    LYHYTAIKAINEN_TOISTUVA_HAITTA,
    PITKAKESTOINEN_TOISTUVA_HAITTA
}

/** NOTE Järjestys täytyy olla pienimmästä suurimpaan */
enum class Tarinahaitta {
    SATUNNAINEN_HAITTA,
    LYHYTAIKAINEN_TOISTUVA_HAITTA,
    PITKAKESTOINEN_TOISTUVA_HAITTA
}

enum class HaittaAjanKestoLuokittelu(override val value: Int) : Luokittelu {
    YLI_KOLME_KUUKAUTTA(5),
    KAKSI_VIIKKOA_VIIVA_KOLME_KUUKAUTTA(3),
    ALLE_KAKSI_VIIKKOA(1)
}

enum class Liikennemaaraluokittelu(override val value: Int) : Luokittelu {
    _10000_TAI_ENEMMAN(5),
    _5000_9999(4),
    _1500_4999(3),
    _500_1499(2),
    ALLE_500(1),
    EI_LIIKENNETTA(0)
}

enum class Pyoraliikenneluokittelu(override val value: Int) : Luokittelu {
    PRIORISOITU_REITTI_TAI_PRIORISOIDUN_REITIN_OSANA_TOIMIVA_KATU(5),
    PAAREITTI_TAI_PAAREITIN_OSANA_TOIMIVA_KATU(4),
    EI_VAIKUTA_PYORALIIKENTEESEEN(0)
}

enum class Linjaautoliikenneluokittelu(override val value: Int) : Luokittelu {
    KAMPPI_RAUTATIENTORI_MANNERHEIMINTIE_KAISANIEMENKATU_HAMEENTIE_TAI_YLI_20_VUOROA_RUUHKATUNNISSA(
        5
    ),
    RUNKOLINJA_TAI_ENINTAAN_20_VUOROA_RUUHKAAIKANA(4),
    RUNKOLINJAMAINEN_LINJA_TAI_ENINTAAN_10_VUOROA_RUUHKAAIKANA(3),
    ENINTAAN_5_VUOROA_RUUHKAAIKANA_TAI_LINJOJA_MUUNA_AIKANA(2),
    EI_VAIKUTA_LINJAAUTOLIIKENTEESEEN(0)
}

enum class Raitioliikenneluokittelu(override val value: Int) : Luokittelu {
    RAITIOTIEVERKON_RATAOSA_JOLLA_SAANNOLLISTA_LINJALIIKENNETTA(5),
    RAITIOTIEVERKON_RATAOSA_JOLLA_EI_SAANNOLLISTA_LINJALIIKENNETTA(3),
    EI_TUNNISTETTUJA_RAITIOTIEKISKOJA(0),
}
