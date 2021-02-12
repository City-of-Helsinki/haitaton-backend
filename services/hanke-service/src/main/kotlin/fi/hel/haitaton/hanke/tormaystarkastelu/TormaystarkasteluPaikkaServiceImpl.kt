package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

class TormaystarkasteluPaikkaServiceImpl (private val tormaystarkasteluDao: TormaystarkasteluDao): TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaarvot which is brought in for some classification information
     */
    override fun calculateTormaystarkasteluLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): List<Luokittelutulos> {

        val luokitteluTulosComplete = mutableListOf<Luokittelutulos>()

        var katuluokkaLuokittelut = getKatuluokkaLuokitteluTulos(hanke, rajaArvot)

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

        var hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            return katuLuokittelu

        val tormaystarkasteluYlreParts = ""   //TODO: call dao check to get the matches
        val tormaystarkasteluYlreClasses = ""   //TODO: call dao check to get the matches


        if (!hitsInYlreParts(tormaystarkasteluYlreParts) && !hitsInYlreClass(tormaystarkasteluYlreClasses)) {
            katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, "0", KatuluokkaTormaysLuokittelu.ZERO.toString()))
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
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, "2", KatuluokkaTormaysLuokittelu.TWO.toString()))
                return katuLuokittelu
            } else {
                //arvo is 1, set and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, "1", KatuluokkaTormaysLuokittelu.ONE.toString()))
                return katuLuokittelu
            }
        }

        if (!hitsInYlreClass(tormaystarkasteluYlreClasses)) {  //ylre_parts yes but still no hit in any usable classification
            katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, "0", KatuluokkaTormaysLuokittelu.ZERO.toString()))
            return katuLuokittelu
        } else {
            //is ylre_class hit 3-5
            //use it 3-5 and leave

            //else (1-2)
            val tormaystarkasteluCentralBusinessArea = "" //TODO: call dao check to get the matches

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, "2", KatuluokkaTormaysLuokittelu.TWO.toString()))
                return katuLuokittelu
            } else {
                //arvo is 1, set and leave
                katuLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, "1", KatuluokkaTormaysLuokittelu.ONE.toString()))
                return katuLuokittelu
            }
        }




        return katuLuokittelu
    }

    private fun maximumInStreetClasses(tormaystarkasteluStreetClasses: String): String {
        TODO("Not yet implemented")
    }

    private fun hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea: String): Boolean {
        TODO("Not yet implemented")
    }

    private fun hitsInYlreClass(tormaystarkasteluYlreClasses: String): Any {
        TODO("Not yet implemented")
    }

    private fun hitsInYlreParts(tormaystarkasteluYlreParts: String): Any {
        TODO("Not yet implemented")
    }

    internal fun getLiikennemaaraLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot, katuluokkaLuokittelut: List<Luokittelutulos>): List<Luokittelutulos> {
        TODO("Not yet implemented")
    }

    internal fun getPyorailyLuokitteluTulos(hanke: Hanke): List<Luokittelutulos> {

        val pyorailyLuokittelu = mutableListOf<Luokittelutulos>()

        var hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null) return pyorailyLuokittelu

        val tormaystarkastelutulos = tormaystarkasteluDao.pyorailyreitit(hankeGeometriatId)

        if (matchesPriorityCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "5", PyorailyTormaysLuokittelu.FIVE.toString()))

        } else if (matchesMainCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "4", PyorailyTormaysLuokittelu.FOUR.toString()))

        } else {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "0", PyorailyTormaysLuokittelu.ZERO.toString()))
        }

        return pyorailyLuokittelu
    }

    private fun matchesMainCycling(tormaystulos: String): Boolean {
        //if contains any rows with ("priority")
        return true
    }

    private fun matchesPriorityCycling(tormaystulos: String): Boolean {
        //if contains any rows with("main")
        return true
    }


}

