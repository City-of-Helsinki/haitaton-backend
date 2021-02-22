package fi.hel.haitaton.hanke.tormaystarkastelu

// we get arvo+selite per luokitteluType per hankeGeometriaId for hanke (hanke can have multiple geometria)
data class Luokittelutulos(val hankeGeometriaId: Int, val luokitteluType: LuokitteluType, val arvo: Int, val explanation: String)

// one type's one arvo rule for minumum value
data class RajaArvo(val luokitteluType: LuokitteluType, val arvo: Int, val minimumValue: Int, val explanation: String)

// TODO: these values must be digged from database eventually
class LuokitteluRajaArvot {
    val bussiliikenneRajaArvot = listOf<RajaArvo>(   // TODO: add explanations
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 5, 21, ""),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 4, 11, ""),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 3, 5, ""),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 2, 0, ""),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 0, -1, "") // no hits
    )

    val liikennemaaraRajaArvot = listOf<RajaArvo>(
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 5, 10000, "10000 tai enemmän"),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 4, 5000, "5 000-9999"),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 3, 1500, "1 500-4999"),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 2, 500, "500 - 1499"),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 1, 1, "Alle 500"),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 0, 0, "Ei autoliikennettä") // no hits
    )
}
