package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

class TormaystarkasteluPaikkaServiceImpl(val tormaystarkasteluDao: TormaystarkasteluDao) : TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaArvot which is brought in for some classification information
     */
    override fun calculateTormaystarkasteluLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): List<Luokittelutulos> {

        val luokitteluTulosComplete = mutableListOf<Luokittelutulos>()

        val katuluokkaLuokittelut = getKatuluokkaLuokitteluTulos(hanke)

        luokitteluTulosComplete.addAll(katuluokkaLuokittelut)
        luokitteluTulosComplete.addAll(getLiikennemaaraLuokitteluTulos(hanke, rajaArvot, katuluokkaLuokittelut))
        luokitteluTulosComplete.addAll(getPyorailyLuokitteluTulos(hanke))
        //TODO: "call methods for deciding separate luokittelu steps for missing luokittelu"
        //bussit
        //raitiovaunut

        return luokitteluTulosComplete
    }

    internal fun getKatuluokkaLuokitteluTulos(hanke: Hanke): List<Luokittelutulos> {

        val katuLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            return katuLuokittelu

        val tormaystarkasteluYlreParts = tormaystarkasteluDao.yleisetKatualueet(hankeGeometriatId)
        val tormaystarkasteluYlreClasses = tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriatId)


        if (hitsInYlreParts(tormaystarkasteluYlreParts) == false && hitsInYlreClass(tormaystarkasteluYlreClasses) == false) {
            katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.ZERO.toString()))
            return katuLuokittelu
        }

        val tormaystarkasteluStreetClasses = tormaystarkasteluDao.katuluokat(hankeGeometriatId)

        if (hitsInStreetClasses(tormaystarkasteluStreetClasses)) { //streetClass exits
            //get max from streetclasses
            if (tormaystarkasteluStreetClasses.containsValue(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)) {
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 5, KatuluokkaTormaysLuokittelu.FIVE.toString()))
                return katuLuokittelu
            }
            if (tormaystarkasteluStreetClasses.containsValue(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)) {
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 4, KatuluokkaTormaysLuokittelu.FOUR.toString()))
                return katuLuokittelu
            }
            if (tormaystarkasteluStreetClasses.containsValue(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)) {
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.THREE.toString()))
                return katuLuokittelu
            }

            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriatId)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.TWO.toString()))
                return katuLuokittelu
            } else {
                //arvo is 1, set and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.ONE.toString()))
                return katuLuokittelu
            }
        }

        if (hitsInYlreClass(tormaystarkasteluYlreClasses) == false) {  //ylre_parts yes but still no hit in any usable classification
            katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.ZERO.toString()))
            return katuLuokittelu

        } else {
            //get max from ylreclasses = yleinen katuluokka
            if (tormaystarkasteluYlreClasses.containsValue(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)) {
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 5, KatuluokkaTormaysLuokittelu.FIVE.toString()))
                return katuLuokittelu
            }
            if (tormaystarkasteluYlreClasses.containsValue(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)) {
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 4, KatuluokkaTormaysLuokittelu.FOUR.toString()))
                return katuLuokittelu
            }
            if (tormaystarkasteluYlreClasses.containsValue(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)) {
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.THREE.toString()))
                return katuLuokittelu
            }
            //central business area
            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriatId)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.TWO.toString()))
                return katuLuokittelu
            } else {
                //arvo is 1, set and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.ONE.toString()))
                return katuLuokittelu
            }
        }

        return katuLuokittelu
    }

    private fun hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea: Map<Int, Boolean>): Boolean {
        if (tormaystarkasteluCentralBusinessArea != null && tormaystarkasteluCentralBusinessArea.size > 0)
            return true
        return false
    }

    private fun hitsInYlreClass(tormaystarkasteluYlreClasses: Map<Int, TormaystarkasteluKatuluokka>): Boolean {
        if (tormaystarkasteluYlreClasses != null && tormaystarkasteluYlreClasses.size > 0)
            return true
        return false

    }

    private fun hitsInStreetClasses(tormaystarkasteluStreetClasses: Map<Int, TormaystarkasteluKatuluokka>): Boolean {
        if (tormaystarkasteluStreetClasses != null && tormaystarkasteluStreetClasses.size > 0)
            return true
        return false
    }


    private fun hitsInYlreParts(tormaystarkasteluYlreParts: Map<Int, Boolean>): Boolean {
        if (tormaystarkasteluYlreParts != null && tormaystarkasteluYlreParts.size > 0)
            return true
        return false
    }

    internal fun getLiikennemaaraLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot, katuluokkaLuokittelut: List<Luokittelutulos>): List<Luokittelutulos> {
        val liikennemaaraLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null) return liikennemaaraLuokittelu

        liikennemaaraLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        return liikennemaaraLuokittelu
    }

    internal fun getPyorailyLuokitteluTulos(hanke: Hanke): List<Luokittelutulos> {

        val pyorailyLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            return pyorailyLuokittelu

        val tormaystarkastelutulos = tormaystarkasteluDao.pyorailyreitit(hankeGeometriatId)

        if (matchesPriorityCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 5, PyorailyTormaysLuokittelu.FIVE.toString()))
        } else if (matchesMainCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 4, PyorailyTormaysLuokittelu.FOUR.toString()))
        } else {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.ZERO.toString()))
        }

        return pyorailyLuokittelu
    }

    private fun matchesMainCycling(tormaystulos: Map<Int, TormaystarkasteluPyorailyreittiluokka>): Boolean {
        //if contains any rows with ("priority")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.name == TormaystarkasteluPyorailyreittiluokka.PAAREITTI.name
        }
    }

    private fun matchesPriorityCycling(tormaystulos: Map<Int, TormaystarkasteluPyorailyreittiluokka>): Boolean {
        //if contains any rows with("main")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.name == TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI.name
        }
    }


}

