package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

class TormaystarkasteluLuokitteluServiceImpl(private val tormaystarkasteluDao: TormaystarkasteluDao) :
    TormaystarkasteluLuokitteluService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaArvot which is brought in for some classification information
     */
    override fun calculateTormaystarkasteluLuokitteluTulos(
        hanke: Hanke,
        rajaArvot: LuokitteluRajaArvot
    ): Map<LuokitteluType, Luokittelutulos> {

        // if no geometries so let's get out of here, this is invalid state
        if (hanke.geometriat == null || hanke.geometriat?.id == null) {
            throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")
        }

        val luokitteluTulosComplete = mutableMapOf<LuokitteluType, Luokittelutulos>()

        luokitteluTulosComplete[LuokitteluType.HAITTA_AJAN_KESTO] = haittaAjanKesto(hanke, rajaArvot)
        luokitteluTulosComplete[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN] =
            todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin(hanke)
        luokitteluTulosComplete[LuokitteluType.KAISTAJARJESTELYN_PITUUS] =
            kaistajarjestelynPituus(hanke)
        val katuluokkaLuokittelu = katuluokkaLuokittelu(hanke)
        luokitteluTulosComplete[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokitteluTulosComplete[LuokitteluType.LIIKENNEMAARA] =
            liikennemaaraLuokittelu(hanke, rajaArvot, katuluokkaLuokittelu)
        luokitteluTulosComplete[LuokitteluType.PYORAILYN_PAAREITTI] = pyorailyLuokittelu(hanke)
        luokitteluTulosComplete[LuokitteluType.RAITIOVAUNULIIKENNE] = raitiovaunuLuokittelu(hanke)
        luokitteluTulosComplete[LuokitteluType.BUSSILIIKENNE] = bussiLuokittelu(hanke, rajaArvot)

        return luokitteluTulosComplete
    }

    internal fun haittaAjanKesto(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): Luokittelutulos {
        val kesto = hanke.haittaAjanKesto ?: throw IllegalArgumentException("Hanke has no start and/or end periods")
        val rajaArvo = rajaArvot.haittaAikaRajaArvot.first { it.minimumValue <= kesto }
        return Luokittelutulos(LuokitteluType.HAITTA_AJAN_KESTO, rajaArvo.arvo, rajaArvo.explanation)
    }

    internal fun todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin(hanke: Hanke): Luokittelutulos {
        val kaistaHaitta = hanke.kaistaHaitta ?: throw IllegalArgumentException("Hanke has no kaistaHaitta")
        return Luokittelutulos(
            LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN,
            kaistaHaitta.arvo,
            kaistaHaitta.kuvaus
        )
    }

    internal fun kaistajarjestelynPituus(hanke: Hanke): Luokittelutulos {
        val kaistajarjestelynPituus =
            hanke.kaistaPituusHaitta ?: throw IllegalArgumentException("Hanke has no kaistaPituusHaitta")
        return Luokittelutulos(
            LuokitteluType.KAISTAJARJESTELYN_PITUUS,
            kaistajarjestelynPituus.arvo,
            kaistajarjestelynPituus.kuvaus
        )
    }

    private fun katuluokkaLuokittelu(hanke: Hanke): Luokittelutulos {

        val hankeGeometriat = hanke.geometriat
            ?: throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val tormaystarkasteluYlreParts = tormaystarkasteluDao.yleisetKatualueet(hankeGeometriat)
        val tormaystarkasteluYlreClasses = tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriat)

        if (!hitsInYlreParts(tormaystarkasteluYlreParts) && !hitsInYlreClass(tormaystarkasteluYlreClasses)) {
            return Luokittelutulos(
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        }

        val tormaystarkasteluStreetClasses = tormaystarkasteluDao.katuluokat(hankeGeometriat)

        if (hitsInStreetClasses(tormaystarkasteluStreetClasses)) { // streetClass exits
            // get max from streetclasses
            if (tormaystarkasteluStreetClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)
            ) {
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    5,
                    KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
                )
            }
            if (tormaystarkasteluStreetClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    4,
                    KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()
                )
            }
            if (tormaystarkasteluStreetClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    3,
                    KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
                )
            }

            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                // arvo is 2 set, and leave
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    2,
                    KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
                )
            } else {
                // arvo is 1, set and leave
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    1,
                    KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
                )
            }
        }

        if (!hitsInYlreClass(tormaystarkasteluYlreClasses)) {
            // ylre_parts yes but still no hit in any usable classification
            return Luokittelutulos(
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        } else {
            // get max from ylreclasses = yleinen katuluokka
            if (tormaystarkasteluYlreClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)
            ) {
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    5,
                    KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
                )
            }
            if (tormaystarkasteluYlreClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    4,
                    KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()
                )
            }
            if (tormaystarkasteluYlreClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    3,
                    KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
                )
            }
            // central business area
            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                // arvo is 2 set, and leave
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    2,
                    KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
                )
            } else {
                // arvo is 1, set and leave
                return Luokittelutulos(
                    LuokitteluType.KATULUOKKA,
                    1,
                    KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
                )
            }
        }
    }

    private fun hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea: Map<Int, Boolean>): Boolean {
        return tormaystarkasteluCentralBusinessArea.isNotEmpty()
    }

    private fun hitsInYlreClass(tormaystarkasteluYlreClasses: Map<Int, Set<TormaystarkasteluKatuluokka>>): Boolean {
        return tormaystarkasteluYlreClasses.isNotEmpty()
    }

    private fun hitsInStreetClasses(
        tormaystarkasteluStreetClasses: Map<Int, Set<TormaystarkasteluKatuluokka>>
    ): Boolean {
        return tormaystarkasteluStreetClasses.isNotEmpty()
    }

    private fun hitsInYlreParts(tormaystarkasteluYlreParts: Map<Int, Boolean>): Boolean {
        return tormaystarkasteluYlreParts.isNotEmpty()
    }

    private fun liikennemaaraLuokittelu(
        hanke: Hanke,
        rajaArvot: LuokitteluRajaArvot,
        katuluokkaLuokittelu: Luokittelutulos?
    ): Luokittelutulos {

        // case when not a street -> no trafic -> leave
        if (katuluokkaLuokittelu?.arvo == 0) {
            return getLiikenneMaaraLowestLuokittelu(rajaArvot)
        }

        // find maximum tormaystulos
        val maximum = getMaximumLiikennemaaraFromVolumes(hanke, katuluokkaLuokittelu)

        // actual classification
        rajaArvot.liikennemaaraRajaArvot.forEach { rajaArvo ->
            if (rajaArvo.minimumValue <= maximum) { // check against max
                return Luokittelutulos(
                    LuokitteluType.LIIKENNEMAARA,
                    rajaArvo.arvo,
                    rajaArvo.explanation
                )
            }
        }

        // return value is needed here, but this should never be needed
        return getLiikenneMaaraLowestLuokittelu(rajaArvot)
    }

    private fun getLiikenneMaaraLowestLuokittelu(rajaArvot: LuokitteluRajaArvot): Luokittelutulos {
        val arvoRivi = rajaArvot.liikennemaaraRajaArvot.first { rajaArvo -> rajaArvo.minimumValue == 0 } // find zero
        return Luokittelutulos(LuokitteluType.LIIKENNEMAARA, arvoRivi.arvo, arvoRivi.explanation)
    }

    private fun getMaximumLiikennemaaraFromVolumes(hanke: Hanke, katuluokkaLuokittelu: Luokittelutulos?): Int {

        var tormaystulos: Map<Int, Set<Int>> = mutableMapOf()

        // type of street (=street class) decides which volume data we use for trafic (buffering of street width varies)
        if (shouldUseSmallerRadiusVolumes(katuluokkaLuokittelu)) {
            // volumes 15 comparison
            tormaystulos = tormaystarkasteluDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        } else if (shouldUseWiderRadiusVolumes(katuluokkaLuokittelu)) {
            // volumes 30 comparison
            tormaystulos = tormaystarkasteluDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        }

        return tormaystulos.values.flatten().maxOrNull() ?: 0
    }

    private fun shouldUseWiderRadiusVolumes(katuluokkaLuokittelu: Luokittelutulos?) =
        4 <= katuluokkaLuokittelu?.arvo!!

    private fun shouldUseSmallerRadiusVolumes(katuluokkaLuokittelu: Luokittelutulos?) =
        katuluokkaLuokittelu?.arvo!! in 1..3  // this is range check

    private fun pyorailyLuokittelu(hanke: Hanke): Luokittelutulos {

        val hankeGeometriat = hanke.geometriat
            ?: throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val tormaystarkastelutulos = tormaystarkasteluDao.pyorailyreitit(hankeGeometriat)

        return when {
            matchesPriorityCycling(tormaystarkastelutulos) -> {
                Luokittelutulos(
                    LuokitteluType.PYORAILYN_PAAREITTI,
                    5,
                    PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()
                )
            }
            matchesMainCycling(tormaystarkastelutulos) -> {
                Luokittelutulos(
                    LuokitteluType.PYORAILYN_PAAREITTI,
                    4,
                    PyorailyTormaysLuokittelu.PAAREITTI.toString()
                )
            }
            else -> {
                Luokittelutulos(
                    LuokitteluType.PYORAILYN_PAAREITTI,
                    0,
                    PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
                )
            }
        }
    }

    private fun matchesMainCycling(tormaystulos: Map<Int, Set<TormaystarkasteluPyorailyreittiluokka>>): Boolean {
        // if contains any rows with ("priority")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)
        }
    }

    private fun matchesPriorityCycling(tormaystulos: Map<Int, Set<TormaystarkasteluPyorailyreittiluokka>>): Boolean {
        // if contains any rows with("main")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI)
        }
    }

    private fun bussiLuokittelu(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): Luokittelutulos {
        val hankeGeometriat = hanke.geometriat
            ?: throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val criticalAreaTormays = tormaystarkasteluDao.bussiliikenteenKannaltaKriittinenAlue(hankeGeometriat)
        if (hitsInCriticalAreaBus(criticalAreaTormays)) {
            // if critical_area matches ->  return 5
            val arvoRivi = rajaArvot.bussiliikenneRajaArvot.first { rajaArvo -> rajaArvo.arvo == 5 }
            return Luokittelutulos(LuokitteluType.BUSSILIIKENNE, arvoRivi.arvo, arvoRivi.explanation)
        }

        val bussesTormaystulos = tormaystarkasteluDao.bussit(hankeGeometriat)

        // if no hits in buses -> 0
        if (!hitsInBusses(bussesTormaystulos)) {
            val arvoRivi = getBussiRajaArvoWithClassification(rajaArvot, 0) // find zero
            return Luokittelutulos(LuokitteluType.BUSSILIIKENNE, arvoRivi.arvo, arvoRivi.explanation)
        }
        // count sum of rush_hours
        val countOfRushHourBuses = calculateCountOfRushHourBuses(bussesTormaystulos)

        // if rush_hours >=21 -> 5
        val arvoRiviTop = getBussiRajaArvoWithClassification(rajaArvot, 5)
        if (countOfRushHourBuses >= arvoRiviTop.minimumValue) {
            return Luokittelutulos(
                LuokitteluType.BUSSILIIKENNE,
                arvoRiviTop.arvo,
                arvoRiviTop.explanation
            )
        }

        // if matchesTrunk=yes -> 4 or if rush_hours 11-20 -> 4
        val arvoRiviSecond = getBussiRajaArvoWithClassification(rajaArvot, 4)
        if (matchesBusLineIsTrunkLine(bussesTormaystulos) || countOfRushHourBuses >= arvoRiviSecond.minimumValue) {
            return Luokittelutulos(
                LuokitteluType.BUSSILIIKENNE,
                arvoRiviSecond.arvo,
                arvoRiviSecond.explanation
            )
        }

        // if matchesAlmost=yes -> 3 or if rush_hour count 5-10 -> 3
        val arvoRiviMiddle = getBussiRajaArvoWithClassification(rajaArvot, 3)
        if (matchesBusLineIsAlmostTrunkLine(bussesTormaystulos) ||
            countOfRushHourBuses >= arvoRiviMiddle.minimumValue
        ) {
            return Luokittelutulos(
                LuokitteluType.BUSSILIIKENNE,
                arvoRiviMiddle.arvo,
                arvoRiviMiddle.explanation
            )
        }

        // if rush_hours 0-4 -> 2
        val arvoRiviSmall = getBussiRajaArvoWithClassification(rajaArvot, 2)
        if (countOfRushHourBuses >= arvoRiviSmall.minimumValue) {
            return Luokittelutulos(
                LuokitteluType.BUSSILIIKENNE,
                arvoRiviSmall.arvo,
                arvoRiviSmall.explanation
            )
        }

        // should not end here, but for safety

        val arvoRivi = getBussiRajaArvoWithClassification(rajaArvot, 0) // find zero
        return Luokittelutulos(LuokitteluType.BUSSILIIKENNE, arvoRivi.arvo, arvoRivi.explanation)
    }

    // There is at least one "Almost trunk" (=runkolinjamainen linja in Finnish) in the analysis result
    private fun matchesBusLineIsAlmostTrunkLine(bussesTormaystulos: Map<Int, Set<TormaystarkasteluBussireitti>>): Boolean {
        val oneList = bussesTormaystulos.values.flatten()
        return oneList.any { tormaystulosRivi ->
            tormaystulosRivi.runkolinja == TormaystarkasteluBussiRunkolinja.LAHES
        }
    }

    // There is at least one "Trunk" (=runkolinja in Finnish) in the analysis result
    private fun matchesBusLineIsTrunkLine(bussesTormaystulos: Map<Int, Set<TormaystarkasteluBussireitti>>): Boolean {
        val oneList = bussesTormaystulos.values.flatten()
        return oneList.any { tormaystulosRivi ->
            tormaystulosRivi.runkolinja == TormaystarkasteluBussiRunkolinja.ON
        }
    }

    // Helper method for getting correct rajaArvo classification for certain arvo
    private fun getBussiRajaArvoWithClassification(rajaArvot: LuokitteluRajaArvot, arvo: Int) =
        rajaArvot.bussiliikenneRajaArvot.first { rajaArvo -> rajaArvo.arvo == arvo }

    // How many rush_hour bus lines there are?
    private fun calculateCountOfRushHourBuses(bussesTormaystulos: Map<Int, Set<TormaystarkasteluBussireitti>>): Int {
        var countOfBuses = 0
        val oneList = bussesTormaystulos.values.flatten()
        oneList.forEach {
            countOfBuses += it.vuoromaaraRuuhkatunnissa
        }

        return countOfBuses
    }

    // Are there any rush_hour bus lines?
    private fun hitsInBusses(bussesTormaystulos: Map<Int, Set<TormaystarkasteluBussireitti>>): Boolean {
        return bussesTormaystulos.isNotEmpty()
    }

    // Are there critical areas for buses?
    private fun hitsInCriticalAreaBus(criticalAreaTormays: Map<Int, Boolean>): Boolean {
        return criticalAreaTormays.isNotEmpty()
    }

    /**
     * Returns classification for tram trafic comparison in Luokittelutulos.
     */
    private fun raitiovaunuLuokittelu(hanke: Hanke): Luokittelutulos {

        val hankeGeometriat = hanke.geometriat
            ?: throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val tormaystarkastelutulos = tormaystarkasteluDao.raitiotiet(hankeGeometriat)

        return when {
            // no trams
            tormaystarkastelutulos.isEmpty() -> Luokittelutulos(
                LuokitteluType.RAITIOVAUNULIIKENNE, 0,
                RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
            )
            // trams have shared lane with cars
            matchesSharedLane(tormaystarkastelutulos) -> Luokittelutulos(
                LuokitteluType.RAITIOVAUNULIIKENNE, 4,
                RaitiovaunuTormaysLuokittelu.JAETTU_KAISTA.toString()
            )
            // own lane for tram
            matchesOwnLane(tormaystarkastelutulos) -> Luokittelutulos(
                LuokitteluType.RAITIOVAUNULIIKENNE, 3,
                RaitiovaunuTormaysLuokittelu.OMA_KAISTA.toString()
            )
            // else (should not have this situation?)
            else -> Luokittelutulos(
                LuokitteluType.RAITIOVAUNULIIKENNE,
                0,
                RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
            )
        }
    }

    // Do trams have shared lane with cars?
    private fun matchesSharedLane(tormaystulos: Map<Int, Set<TormaystarkasteluRaitiotiekaistatyyppi>>): Boolean {
        // if contains any rows with ("mixed")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU)
        }
    }

    // Do trams have their own dedicated lane in streets?
    private fun matchesOwnLane(tormaystulos: Map<Int, Set<TormaystarkasteluRaitiotiekaistatyyppi>>): Boolean {
        // if contains any rows with ("dedicated")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluRaitiotiekaistatyyppi.OMA)
        }
    }
}
