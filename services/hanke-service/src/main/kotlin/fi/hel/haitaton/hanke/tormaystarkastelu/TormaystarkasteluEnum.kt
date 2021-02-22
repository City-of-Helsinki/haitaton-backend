package fi.hel.haitaton.hanke.tormaystarkastelu

enum class LuokitteluType {

    KATULUOKKA,
    LIIKENNEMAARA,
    PYORAILYN_PAAREITTI,
    BUSSILIIKENNE,
    RAITIOVAUNULIIKENNE

    // TODO: add types that come from hanke "text data"
}

enum class PyorailyTormaysLuokittelu(s: String) {
    PRIORISOITU_REITTI("Pyöräilyn priorisoidut reitit / priorisoidun reitin osana toimiva katu"),
    PAAREITTI("Pyöräilyn pääreitti / pääreitin osana toimiva katu"),
    EI_PYORAILUREITTI("Ei vaikuta pyöräliikenteeseen")
}

enum class RaitiovaunuTormaysLuokittelu(s: String) {
    JAETTU_KAISTA( "Raitiovaunut samalla kaistalla autojen kanssa"),
    OMA_KAISTA ( "Raitiovaunuilla oma kaista"),
    EI_RAITIOVAUNULIIKENNETTA ( "Ei vaikuta raitiovaunuliikenteeseen")
}
enum class BussiTormaysLuokittelu(s: String) {
    EI_BUSSILIIKENNETTA( "Ei vaikuta linja-autoliikenteeseen")
}

enum class KatuluokkaTormaysLuokittelu(s: String) {
    PAAKATU_MOOTTORIVAYLA("Pääkatu tai moottoriväylä"),
    ALUEELLINEN_KOKOOJA("Alueellinen kokoojakatu"),
    PAIKALLINEN_KOKOOJA("Paikallinen kokoojakatu"),
    KANTAKAUPUNGIN_TONTTIKATU("Kantakaupungin tonttikatu"),
    MUU_TONTTIKATU_ALUE("Muu tonttikatu tai alue"),
    EI_MOOTTORILIIKENNE_VAIK("Ei vaikuta moottoriajoneuvoliikenteeseen")
}

enum class LiikenneMaaraTormaysLuokittelu(s: String) {
    FIVE("10 000 tai enemmän"),
    FOUR("5 000-9 999"),
    THREE("1 500-4 999"),
    TWO("500 - 1 499"),
    ONE("Alle 500"),
    ZERO("Ei autoliikennettä")
}
