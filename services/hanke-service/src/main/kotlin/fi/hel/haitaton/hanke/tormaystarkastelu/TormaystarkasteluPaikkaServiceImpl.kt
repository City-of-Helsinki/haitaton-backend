package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.InvalidStateException
import fi.hel.haitaton.hanke.TormaysAnalyysiException
import fi.hel.haitaton.hanke.domain.Hanke

class TormaystarkasteluPaikkaServiceImpl(val tormaystarkasteluDao: TormaystarkasteluDao) : TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaArvot which is brought in for some classification information
     */
    override fun calculateTormaystarkasteluLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): List<Luokittelutulos> {

        val hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            throw InvalidStateException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val luokitteluTulosComplete = mutableListOf<Luokittelutulos>()

        val katuluokkaLuokittelu = getKatuluokkaLuokitteluTulos(hanke)

        if (katuluokkaLuokittelu != null) {
            luokitteluTulosComplete.add(katuluokkaLuokittelu)
            luokitteluTulosComplete.addAll(getLiikennemaaraLuokitteluTulos(hanke, rajaArvot, katuluokkaLuokittelu))
        } else {
            throw TormaysAnalyysiException("Katuluokka not resolved for Hanke id ${hanke.id}")
        }
        luokitteluTulosComplete.addAll(getPyorailyLuokitteluTulos(hanke))
        //TODO: "call methods for deciding separate luokittelu steps for missing luokittelu"
        //bussit
        //raitiovaunut

        return luokitteluTulosComplete
    }

    internal fun getKatuluokkaLuokitteluTulos(hanke: Hanke): Luokittelutulos? {

        val katuLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriat = hanke.geometriat
        val hankeGeometriatId = hankeGeometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            return null

        val tormaystarkasteluYlreParts = tormaystarkasteluDao.yleisetKatualueet(hankeGeometriat)
        val tormaystarkasteluYlreClasses = tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriat)

        if (hitsInYlreParts(tormaystarkasteluYlreParts) == false && hitsInYlreClass(tormaystarkasteluYlreClasses) == false) {
            return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString())
        }

        val tormaystarkasteluStreetClasses = tormaystarkasteluDao.katuluokat(hankeGeometriat)

        if (hitsInStreetClasses(tormaystarkasteluStreetClasses)) { //streetClass exits
            //get max from streetclasses
            if (tormaystarkasteluStreetClasses.values.flatten().contains(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)) {
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 5, KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString())
            }
            if (tormaystarkasteluStreetClasses.values.flatten().contains(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)) {
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 4, KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString())
            }
            if (tormaystarkasteluStreetClasses.values.flatten().contains(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)) {
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString())

            }

            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString())

            } else {
                //arvo is 1, set and leave
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString())
            }
        }

        if (hitsInYlreClass(tormaystarkasteluYlreClasses) == false) {  //ylre_parts yes but still no hit in any usable classification
            return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString())
        } else {
            //get max from ylreclasses = yleinen katuluokka
            if (tormaystarkasteluYlreClasses.values.flatten().contains(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)) {
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 5, KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString())
            }
            if (tormaystarkasteluYlreClasses.values.flatten().contains(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)) {
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 4, KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString())
            }
            if (tormaystarkasteluYlreClasses.values.flatten().contains(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)) {
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString())
            }
            //central business area
            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString())

            } else {
                //arvo is 1, set and leave
                return Luokittelutulos(hankeGeometriatId, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString())

            }
        }
    }

    private fun hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea: Map<Int, Boolean>): Boolean {
        if (tormaystarkasteluCentralBusinessArea != null && tormaystarkasteluCentralBusinessArea.size > 0)
            return true
        return false
    }

    private fun hitsInYlreClass(tormaystarkasteluYlreClasses: Map<Int, Set<TormaystarkasteluKatuluokka>>): Boolean {
        if (tormaystarkasteluYlreClasses != null && tormaystarkasteluYlreClasses.size > 0)
            return true
        return false

    }

    private fun hitsInStreetClasses(tormaystarkasteluStreetClasses: Map<Int, Set<TormaystarkasteluKatuluokka>>): Boolean {
        if (tormaystarkasteluStreetClasses != null && tormaystarkasteluStreetClasses.size > 0)
            return true
        return false
    }


    private fun hitsInYlreParts(tormaystarkasteluYlreParts: Map<Int, Boolean>): Boolean {
        if (tormaystarkasteluYlreParts != null && tormaystarkasteluYlreParts.size > 0)
            return true
        return false
    }

    internal fun getLiikennemaaraLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot, katuluokkaLuokittelu: Luokittelutulos?): List<Luokittelutulos> {
        val liikennemaaraLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriatId = hanke.geometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null) return liikennemaaraLuokittelu

        val tormaystulos: Pair<Int, Int>

        // case when not a street -> no trafic -> leave
        if (katuluokkaLuokittelu?.arvo == 0) {
            liikennemaaraLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
            return liikennemaaraLuokittelu
        }

        // type of street (=street class) decides which volume data we use for trafic (buffering of street width varies)
        if (1 <= katuluokkaLuokittelu?.arvo!! && katuluokkaLuokittelu?.arvo!! <= 3) {
            //volumes 15 comparison
            tormaystulos = tormaystarkasteluDao.liikennemaara(hankeGeometriatId, 15) //TODO real call and use enum

        } else if (4 <= katuluokkaLuokittelu?.arvo!!) {
            //volumes 30 comparison
            tormaystulos = tormaystarkasteluDao.liikennemaara(hankeGeometriatId, 30) //TODO real call and use enum
        }

        //find maximum tormaystulos

        rajaArvot.liikennemaaraRajaArvot. forEach {
            rajaArvo ->
            if(rajaArvo.minimumValue >= tormaystulos.max) {  //check against max
                liikennemaaraLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.LIIKENNEMAARA, rajaArvo.arvo, rajaArvo.explanation))

            }
        }


        //actual classification
        rajaArvot.liikennemaaraRajaArvot.firstOrNull { rajaArvo -> rajaArvo.arvo == 5  }?.minimumValue
        rajaArvot.liikennemaaraRajaArvot.firstOrNull { rajaArvo -> rajaArvo.arvo == 5  }?.minimumValue
        rajaArvot.liikennemaaraRajaArvot.firstOrNull { rajaArvo -> rajaArvo.arvo == 5  }?.minimumValue
        rajaArvot.liikennemaaraRajaArvot.firstOrNull { rajaArvo -> rajaArvo.arvo == 5  }?.minimumValue
        rajaArvot.liikennemaaraRajaArvot.firstOrNull { rajaArvo -> rajaArvo.arvo == 5  }?.minimumValue

        return liikennemaaraLuokittelu
    }

    internal fun getPyorailyLuokitteluTulos(hanke: Hanke): List<Luokittelutulos> {

        val pyorailyLuokittelu = mutableListOf<Luokittelutulos>()

        val hankeGeometriat = hanke.geometriat
        val hankeGeometriatId = hankeGeometriat?.id
        //if no id let's get out of here
        if (hankeGeometriatId == null)
            return pyorailyLuokittelu

        val tormaystarkastelutulos = tormaystarkasteluDao.pyorailyreitit(hankeGeometriat)

        if (matchesPriorityCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 5, PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()))
        } else if (matchesMainCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 4, PyorailyTormaysLuokittelu.PAAREITTI.toString()))
        } else {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
        }

        return pyorailyLuokittelu
    }

    private fun matchesMainCycling(tormaystulos: Map<Int, Set<TormaystarkasteluPyorailyreittiluokka>>): Boolean {
        //if contains any rows with ("priority")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)
        }
    }

    private fun matchesPriorityCycling(tormaystulos: Map<Int, Set<TormaystarkasteluPyorailyreittiluokka>>): Boolean {
        //if contains any rows with("main")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI)
        }
    }


}

