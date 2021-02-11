package fi.hel.haitaton.hanke.tormaystarkastelu

enum class LuokitteluType {

    KATULUOKKA,
    LIIKENNEMAARA,
    PYORAILYN_PAAREITTI,
    BUSSILIIKENNE,
    RAITIOVAUNULIIKENNE

    //TODO: add types that come from hanke "text data"
}


enum class PyorailyTormaysLuokittelu(s: String) {
    FIVE( "Pyöräilyn priorisoidut reitit / priorisoidun reitin osana toimiva katu"),
    FOUR ( "Pyöräilyn pääreitti / pääreitin osana toimiva katu"),
    ZERO ( "Ei vaikuta pyöräliikenteeseen")
}

enum class LiikenneMaaraTormaysLuokittelu(s: String) {
    FIVE( "Pääkatu tai moottoriväylä"),
    FOUR ( "Alueellinen kokoojakatu"),
    THREE ( "Paikallinen kokoojakatu"),
    TWO ( "Kantakaupungin tonttikatu"),
    ONE ( "Muu tonttikatutai alue"),
    ZERO ( "Ei vaikuta moottoriajoneuvoliikenteeseen")
}

enum class KatuluokkaTormaysLuokittelu(s: String) {
    FIVE( "10 000 tai enemmän"),
    FOUR ( "5 000-9 999"),
    THREE ( "1 500-4 999"),
    TWO ( "500 - 1 499"),
    ONE ( "Alle 500"),
    ZERO ( "Ei autoliikennettä")
}