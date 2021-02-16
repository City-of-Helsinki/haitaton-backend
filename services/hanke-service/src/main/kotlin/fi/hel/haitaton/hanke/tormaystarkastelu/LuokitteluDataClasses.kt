package fi.hel.haitaton.hanke.tormaystarkastelu


//we get arvo+selite per luokitteluType per hankeGeometriaId for hanke (hanke can have multiple geometria)
data class Luokittelutulos(val hankeGeometriaId: Int, val luokitteluType: LuokitteluType, val arvo: Int, val selite: String) {
}

//one type's one arvo rule for minumum value
data class RajaArvo(val luokitteluType: LuokitteluType, val arvo: Int, val minimumValue: Int) {
}

//TODO: these values must be digged from database eventually
class LuokitteluRajaArvot {
    val bussiliikenneRajaArvot = mutableListOf<RajaArvo>(
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 5, 21),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 4, 11),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 3, 5),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 2, 0),
            RajaArvo(LuokitteluType.BUSSILIIKENNE, 0, -1) // no hits
    )

    val liikennemaaraRajaArvot = mutableListOf<RajaArvo>(
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 5, 10000),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 4, 5000),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 3, 1500),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 2, 500),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 1, 1),
            RajaArvo(LuokitteluType.LIIKENNEMAARA, 0, 0) // no hits
    )
}