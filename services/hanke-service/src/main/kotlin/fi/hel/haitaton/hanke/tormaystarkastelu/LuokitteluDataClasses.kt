package fi.hel.haitaton.hanke.tormaystarkastelu


//we get arvo+selite per luokitteluType per hankeGeometriaId for hanke (hanke can have multiple geometria)
data class Luokittelutulos (val hankeGeometriaId: Int , val luokitteluType: String, val arvo: String, val selite: String) {

}

//one type's one arvo rule for minumum value
data class Rajaarvo(val luokitteluType: String, val arvo: String, val minimumValue:Int) {

}

//TODO: these values must be digged from database eventually
class LuokitteluRajaarvot {
    val bussiliikenneRajaarvot = mutableListOf<Rajaarvo>(
            Rajaarvo("bussi","5", 21),
            Rajaarvo("bussi","4", 11),
            Rajaarvo("bussi","3", 5),
            Rajaarvo("bussi","2", 0),
            Rajaarvo("bussi","0", -1) // no hits
    )

    val liikennemaaraRajaarvot = mutableListOf<Rajaarvo>(
            Rajaarvo("liikennemaara","5", 10000),
            Rajaarvo("liikennemaara","4", 5000),
            Rajaarvo("liikennemaara","3", 1500),
            Rajaarvo("liikennemaara","2", 500),
            Rajaarvo("liikennemaara","1", 1) ,
            Rajaarvo("liikennemaara","0", 0) // no hits
    )

}