package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

class TormaystarkasteluPaikkaServiceImpl(val tormaystarkasteluDao: TormaystarkasteluDao) : TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaArvot which is brought in for some classification information
     */
    override fun calculateTormaystarkasteluLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): List<Luokittelutulos> {

        val luokitteluTulosComplete = mutableListOf<Luokittelutulos>()

        val katuluokkaLuokittelut = getKatuluokkaLuokitteluTulos(hanke, rajaArvot)

        luokitteluTulosComplete.addAll(katuluokkaLuokittelut)
        luokitteluTulosComplete.addAll(getLiikennemaaraLuokitteluTulos(hanke, rajaArvot, katuluokkaLuokittelut))
        luokitteluTulosComplete.addAll(getPyorailyLuokitteluTulos(hanke))
        //TODO: "call methods for deciding separate luokittelu steps for missing luokittelu"
        //bussit
        //raitiovaunut

        return luokitteluTulosComplete
    }


    internal fun getKatuluokkaLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): List<Luokittelutulos> {

        val katuLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            return katuLuokittelu

        val tormaystarkasteluYlreParts = ""   //TODO: call dao check to get the matches
        val tormaystarkasteluYlreClasses = ""   //TODO: call dao check to get the matches


        if (hitsInYlreParts(tormaystarkasteluYlreParts) == false && hitsInYlreClass(tormaystarkasteluYlreClasses) == false) {
            katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.ZERO.toString()))
            return katuLuokittelu
        }

        val tormaystarkasteluStreetClasses = "" //TODO: call dao check to get the matches

        val maximumStreetClass = maximumInStreetClasses(tormaystarkasteluStreetClasses)
        if (maximumStreetClass != null) { //streetClass exits
            //get max from streetclasses
            if (maximumStreetClass == "5" || maximumStreetClass == "4" || maximumStreetClass == "3") {
                //is it 3-5, use it and leave
            } else {
                //else (1-2)
            }
            val tormaystarkasteluCentralBusinessArea = "" //TODO: call dao check to get the matches

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
            //is ylre_class hit 3-5
            //use it 3-5 and leave

            //else (1-2)
            val tormaystarkasteluCentralBusinessArea = "" //TODO: call dao check to get the matches

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

    private fun maximumInStreetClasses(tormaystarkasteluStreetClasses: String): String {
        return "" //TODO implement
    }

    private fun hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea: String): Boolean {
        return false //TODO implement
    }

    private fun hitsInYlreClass(tormaystarkasteluYlreClasses: String): Boolean {
        return false //TODO implement
    }

    private fun hitsInYlreParts(tormaystarkasteluYlreParts: String): Boolean {
        return false //TODO implement
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
            return pyorailyLuokittelu

        } else if (matchesMainCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 4, PyorailyTormaysLuokittelu.FOUR.toString()))
            return pyorailyLuokittelu

        } else {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.ZERO.toString()))
            return pyorailyLuokittelu
        }

        return pyorailyLuokittelu
    }

    private fun matchesMainCycling(tormaystulos: List<PyorailyTormaystarkastelu>): Boolean {
        //if contains any rows with ("priority")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.reittiluokka.name == TormaystarkasteluPyorailyreittiluokitus.PAAREITTI.name
        }
    }

    private fun matchesPriorityCycling(tormaystulos: List<PyorailyTormaystarkastelu>): Boolean {
        //if contains any rows with("main")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.reittiluokka.name == TormaystarkasteluPyorailyreittiluokitus.PRIORISOITU_REITTI.name
        }
    }


}

