package fi.hel.haitaton.hanke.tormaystarkastelu

interface TormaystarkasteluDao {
    /**
     * Checks which general street areas ("yleinen katualue", ylre_parts) these Hanke geometries are on
     */
    fun yleisetKatualueet(hankegeometriatId: Int): Map<Int, Boolean>

    /**
     * Checks which general street classes ("yleinen katuluokka", ylre_classes) these Hanke geometrias are on
     */
    fun yleisetKatuluokat(hankegeometriatId: Int): Map<Int, TormaystarkasteluKatuluokka>

    /**
     * Checks which street classes ("katuluokka", street_classes) these Hanke geometrias are on
     */
    fun katuluokat(hankegeometriatId: Int): Map<Int, TormaystarkasteluKatuluokka>

    /**
     * Checks which of these Hanke geometries are located on central business area ("kantakapunki", central_business_area)
     */
    fun kantakaupunki(hankegeometriatId: Int): Map<Int, Boolean>

    /**
     * Checks whether these Hanke geometries are on any categorized cycling route ("pyöräilyreitti", cycleways_priority/cycleways_main)
     */
    fun pyorailyreitit(hankegeometriatId: Int): Map<Int, TormaystarkasteluPyorailyreittiluokka>
}
