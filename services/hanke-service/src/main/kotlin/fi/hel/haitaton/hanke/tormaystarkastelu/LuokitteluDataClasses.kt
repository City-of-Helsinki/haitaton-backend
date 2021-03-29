package fi.hel.haitaton.hanke.tormaystarkastelu

data class Luokittelutulos(
    val luokitteluType: LuokitteluType,
    val arvo: Int,
    val explanation: String
) {

    // we get arvo+selite per luokitteluType per hankeGeometriaId for hanke (hanke can have multiple geometria)
    // Note: This is not used at the moment until we have a geometry-specific Törmäystarkastelu
    var hankeGeometriaId: Int = 0
}

// one type's one arvo rule for minumum value
data class RajaArvo(val luokitteluType: LuokitteluType, val arvo: Int, val minimumValue: Int, val explanation: String)

// TODO: these values must be digged from database eventually, now they are hardcoded
class LuokitteluRajaArvot {

    val haittaAikaRajaArvot = listOf(
        RajaArvo(
            LuokitteluType.HAITTA_AJAN_KESTO,
            5,
            91,
            "Kesto yli 3 kuukautta"
        ),
        RajaArvo(
            LuokitteluType.HAITTA_AJAN_KESTO,
            3,
            14,
            "Kesto 2 viikkoa - 3 kuukautta"
        ),
        RajaArvo(
            LuokitteluType.HAITTA_AJAN_KESTO,
            1,
            0,
            "Kesto alle 2 viikkoa"
        )
    )
    val bussiliikenneRajaArvot = listOf(
        RajaArvo(
            LuokitteluType.BUSSILIIKENNE,
            5,
            21,
            "Kamppi-Rautatientori -alue, Mannerheimintie, Kaisaniemenkatu, Hämeentie tai vuoromäärä yli 20 ruuhkatunnissa"
        ),
        RajaArvo(
            LuokitteluType.BUSSILIIKENNE,
            4,
            11,
            "Runkolinja tai ruuhka-ajan vuoromäärä enintään 20 ruuhkatunnissa"
        ),
        RajaArvo(
            LuokitteluType.BUSSILIIKENNE,
            3,
            5,
            "Runkolinjamainen linja tai ruuhka-ajan vuoromäärä enintään 10 ruuhkatunnissa"
        ),
        RajaArvo(
            LuokitteluType.BUSSILIIKENNE,
            2,
            0,
            "Linjojen ruuhka-ajan vuoromäärä enintään 5 ruuhkatunnissa tai linjoja muuna aikana"
        ),
        RajaArvo(LuokitteluType.BUSSILIIKENNE, 0, -1, "Ei vaikuta linja-autoliikenteeseen") // no hits

    )

    val liikennemaaraRajaArvot = listOf(
        RajaArvo(LuokitteluType.LIIKENNEMAARA, 5, 10000, "10000 tai enemmän"),
        RajaArvo(LuokitteluType.LIIKENNEMAARA, 4, 5000, "5 000-9999"),
        RajaArvo(LuokitteluType.LIIKENNEMAARA, 3, 1500, "1 500-4999"),
        RajaArvo(LuokitteluType.LIIKENNEMAARA, 2, 500, "500 - 1499"),
        RajaArvo(LuokitteluType.LIIKENNEMAARA, 1, 1, "Alle 500"),
        RajaArvo(LuokitteluType.LIIKENNEMAARA, 0, 0, "Ei autoliikennettä") // no hits
    )
}
