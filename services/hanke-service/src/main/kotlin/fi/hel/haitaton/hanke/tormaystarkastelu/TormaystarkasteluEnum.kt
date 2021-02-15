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