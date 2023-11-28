package fi.hel.haitaton.hanke.tormaystarkastelu

enum class LuokitteluType {
    HAITTA_AJAN_KESTO,
    TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN,
    KAISTAJARJESTELYN_PITUUS,
    KATULUOKKA,
    LIIKENNEMAARA
}

interface Luokittelu {
    val explanation: String
    val value: Int
}

enum class HaittaAjanKestoLuokittelu(override val value: Int, override val explanation: String) :
    Luokittelu {
    YLI_3KK(5, "Kesto yli 3 kuukautta"),
    ALLE_3KK_YLI_2VK(3, "Kesto 2 viikkoa - 3 kuukautta"),
    ALLE_2VK(1, "Kesto alle 2 viikkoa")
}

enum class LiikenneMaaraLuokittelu(override val value: Int, override val explanation: String) :
    Luokittelu {
    YLI_10K(5, "10000 tai enemmän"),
    _5K_10K(4, "5 000-9999"),
    _1500_5K(3, "1 500-4999"),
    _500_1500(2, "500 - 1499"),
    ALLE_500(1, "Alle 500"),
    EI_LIIKENNETTA(0, "Ei autoliikennettä")
}

enum class PyorailyTormaysLuokittelu(override val value: Int, override val explanation: String) :
    Luokittelu {
    PRIORISOITU_REITTI(5, "Pyöräilyn priorisoidut reitit / priorisoidun reitin osana toimiva katu"),
    PAAREITTI(4, "Pyöräilyn pääreitti / pääreitin osana toimiva katu"),
    EI_PYORAILUREITTI(0, "Ei vaikuta pyöräliikenteeseen")
}

enum class BussiLiikenneLuokittelu(override val value: Int, override val explanation: String) :
    Luokittelu {
    KAMPPI_RAUTATIENTORI(
        5,
        "Kamppi-Rautatientori -alue, Mannerheimintie, Kaisaniemenkatu, Hämeentie tai vuoromäärä yli 20 ruuhkatunnissa"
    ),
    RUNKOLINJA(4, "Runkolinja tai ruuhka-ajan vuoromäärä enintään 20 ruuhkatunnissa"),
    RUNKOLINJAMAINEN(
        3,
        "Runkolinjamainen linja tai ruuhka-ajan vuoromäärä enintään 10 ruuhkatunnissa"
    ),
    PERUS(2, "Linjojen ruuhka-ajan vuoromäärä enintään 5 ruuhkatunnissa tai linjoja muuna aikana"),
    EI_VAIKUTA(0, "Ei vaikuta linja-autoliikenteeseen")
}

enum class RaitiotieTormaysLuokittelu(override val value: Int, override val explanation: String) :
    Luokittelu {
    RAITIOTIELINJA(5, "Raitiotieverkon rataosa, jolla on säännöllistä linjaliikennettä"),
    RAITIOTIEVERKON_RATAOSA(
        3,
        "Raitiotieverkon rataosa, joilla ei ole säännöllistä linjaliikennettä"
    ),
    EI_RAITIOTIETA(0, "Ei tunnistettuja raitiotiekiskoja"),
}
