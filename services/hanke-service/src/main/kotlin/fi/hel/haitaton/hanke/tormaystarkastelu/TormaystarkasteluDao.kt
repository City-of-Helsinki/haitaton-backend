package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.geometria.HankeGeometriat

interface TormaystarkasteluDao {
    /**
     * Checks which general street areas ("yleinen katualue", ylre_parts) these Hanke geometries are on
     *
     * @return true for all those geometries that are located on general street area. If returned map is empty then none of the geometries are on a general street area.
     */
    fun yleisetKatualueet(hankegeometriat: HankeGeometriat): Map<Int, Boolean>

    /**
     * Checks which general street classes ("yleinen katuluokka", ylre_classes) these Hanke geometrias are on
     *
     * @return for each Hanke geometry a set of TormaystarkasteluKatuluokka of which that geometry is located on
     */
    fun yleisetKatuluokat(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluKatuluokka>>

    /**
     * Checks which street classes ("katuluokka", street_classes) these Hanke geometrias are on
     *
     * @return for each Hanke geometry a set of TormaystarkasteluKatuluokka for the roads that geometry is located on
     */
    fun katuluokat(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluKatuluokka>>

    /**
     * Checks which of these Hanke geometries are located on central business area ("kantakapunki", central_business_area)
     *
     * @return true for all those geometries that are located on central business area. If returned map is empty then none of the geometries are on central business area.
     */
    fun kantakaupunki(hankegeometriat: HankeGeometriat): Map<Int, Boolean>

    /**
     * Collects all traffic counts ("liikennemäärä") for each Hanke geometry within given radius.
     */
    fun liikennemaarat(
        hankegeometriat: HankeGeometriat,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Map<Int, Set<Int>>

    /**
     * Checks which Hanke geometries are located on critical area for buses
     */
    fun bussiliikenteenKannaltaKriittinenAlue(hankegeometriat: HankeGeometriat): Map<Int, Boolean>

    /**
     * Collects all bus routes for each Hanke geometry.
     *
     * @return for each Hanke geometry a set of TormaystarkasteluBussireitti for the bus routes that geometry is located on
     */
    fun bussit(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluBussireitti>>

    /**
     * Collects tram lane types for each Hanke geometry
     *
     * @return for each Hanke geometry a set of TormaystarkasteluRaitiotiekaistatyyppi for the tram lane types that geometry is located on
     */
    fun raitiotiet(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluRaitiotiekaistatyyppi>>

    /**
     * Checks whether these Hanke geometries are on any categorized cycling route ("pyöräilyreitti", cycleways_priority/cycleways_main)
     *
     * @return for each Hanke geometry a set of TormaystarkasteluPyorailyreittiluokka for the cycleways that geometry is located on
     */
    fun pyorailyreitit(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluPyorailyreittiluokka>>
}
