package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.TormaysAnalyysiException
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat

class TormaystarkasteluPaikkaServiceImpl(private val tormaystarkasteluDao: TormaystarkasteluDao) :
    TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaArvot which is brought in for some classification information
     */
    override fun calculateTormaystarkasteluLuokitteluTulos(
        hanke: Hanke,
        rajaArvot: LuokitteluRajaArvot
    ): List<Luokittelutulos> {

        //if no geometries so let's get out of here, this is invalid state
        if (hanke.geometriat == null || hanke.geometriat?.id == null)
            throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val luokitteluTulosComplete = mutableListOf<Luokittelutulos>()

        val katuluokkaLuokittelu = getKatuluokkaLuokitteluTulos(hanke)

        luokitteluTulosComplete.add(katuluokkaLuokittelu)
        luokitteluTulosComplete.add(getLiikennemaaraLuokitteluTulos(hanke, rajaArvot, katuluokkaLuokittelu))
        luokitteluTulosComplete.add(getPyorailyLuokitteluTulos(hanke))
        luokitteluTulosComplete.add(getRaitiovaunuLuokitteluTulos(hanke))
        luokitteluTulosComplete.add(getBussiLuokitteluTulos(hanke, rajaArvot))

        return luokitteluTulosComplete
    }


    private fun getKatuluokkaLuokitteluTulos(hanke: Hanke): Luokittelutulos {

        val hankeGeometriat = hanke.geometriat
        //if no id let's get out of here
        val hankeGeometriatId = hankeGeometriat?.id
            ?: throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val tormaystarkasteluYlreParts = tormaystarkasteluDao.yleisetKatualueet(hankeGeometriat)
        val tormaystarkasteluYlreClasses = tormaystarkasteluDao.yleisetKatuluokat(hankeGeometriat)

        if (!hitsInYlreParts(tormaystarkasteluYlreParts) && !hitsInYlreClass(tormaystarkasteluYlreClasses)) {
            return Luokittelutulos(
                hankeGeometriatId,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        }

        val tormaystarkasteluStreetClasses = tormaystarkasteluDao.katuluokat(hankeGeometriat)

        if (hitsInStreetClasses(tormaystarkasteluStreetClasses)) { //streetClass exits
            //get max from streetclasses
            if (tormaystarkasteluStreetClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)
            ) {
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    5,
                    KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
                )
            }
            if (tormaystarkasteluStreetClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    4,
                    KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()
                )
            }
            if (tormaystarkasteluStreetClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    3,
                    KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
                )
            }

            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    2,
                    KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
                )
            } else {
                //arvo is 1, set and leave
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    1,
                    KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
                )
            }
        }

        if (!hitsInYlreClass(tormaystarkasteluYlreClasses)) {  //ylre_parts yes but still no hit in any usable classification
            return Luokittelutulos(
                hankeGeometriatId,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        } else {
            //get max from ylreclasses = yleinen katuluokka
            if (tormaystarkasteluYlreClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)
            ) {
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    5,
                    KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
                )
            }
            if (tormaystarkasteluYlreClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    4,
                    KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()
                )
            }
            if (tormaystarkasteluYlreClasses.values.flatten()
                    .contains(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)
            ) {
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    3,
                    KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
                )
            }
            //central business area
            val tormaystarkasteluCentralBusinessArea = tormaystarkasteluDao.kantakaupunki(hankeGeometriat)

            if (hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea)) {
                //arvo is 2 set, and leave
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    2,
                    KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
                )
            } else {
                //arvo is 1, set and leave
                return Luokittelutulos(
                    hankeGeometriatId,
                    LuokitteluType.KATULUOKKA,
                    1,
                    KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
                )
            }
        }
    }

    private fun hitsInCentralBusinessArea(tormaystarkasteluCentralBusinessArea: Map<Int, Boolean>): Boolean {
        if (tormaystarkasteluCentralBusinessArea.isNotEmpty())
            return true
        return false
    }

    private fun hitsInYlreClass(tormaystarkasteluYlreClasses: Map<Int, Set<TormaystarkasteluKatuluokka>>): Boolean {
        if (tormaystarkasteluYlreClasses.isNotEmpty())
            return true
        return false

    }

    private fun hitsInStreetClasses(tormaystarkasteluStreetClasses: Map<Int, Set<TormaystarkasteluKatuluokka>>): Boolean {
        if (tormaystarkasteluStreetClasses.isNotEmpty())
            return true
        return false
    }


    private fun hitsInYlreParts(tormaystarkasteluYlreParts: Map<Int, Boolean>): Boolean {
        if (tormaystarkasteluYlreParts.isNotEmpty())
            return true
        return false
    }

    internal fun getLiikennemaaraLuokitteluTulos(
        hanke: Hanke,
        rajaArvot: LuokitteluRajaArvot,
        katuluokkaLuokittelu: Luokittelutulos?
    ): Luokittelutulos {

        // case when not a street -> no trafic -> leave
        if (katuluokkaLuokittelu?.arvo == 0) {
            return getLiikenneMaaraLowestLuokittelu(hanke.geometriat!!.id!!, rajaArvot)
        }

        //find maximum tormaystulos
        val maximum = getMaximumLiikennemaaraFromVolumes(hanke, katuluokkaLuokittelu)
        if (maximum == null)
            throw TormaysAnalyysiException("Liikennemaara comparison went wrong for hankeId=${hanke.id}")

        //actual classification
        rajaArvot.liikennemaaraRajaArvot.forEach { rajaArvo ->
            if (rajaArvo.minimumValue <= maximum) {  //check against max
                return Luokittelutulos(
                    hanke.geometriat!!.id!!,
                    LuokitteluType.LIIKENNEMAARA,
                    rajaArvo.arvo,
                    rajaArvo.explanation
                )
            }
        }

        //return value is needed here, but this should never be needed
        return getLiikenneMaaraLowestLuokittelu(hanke.geometriat!!.id!!, rajaArvot)
    }

    private fun getLiikenneMaaraLowestLuokittelu( hankeGeometriatId: Int, rajaArvot: LuokitteluRajaArvot): Luokittelutulos {
        val arvoRivi = rajaArvot.liikennemaaraRajaArvot.first { rajaArvo -> rajaArvo.minimumValue == 0 } //find zero
        return Luokittelutulos(hankeGeometriatId, LuokitteluType.LIIKENNEMAARA, arvoRivi.arvo, arvoRivi.explanation)
    }

    private fun getMaximumLiikennemaaraFromVolumes(hanke: Hanke, katuluokkaLuokittelu: Luokittelutulos?): Int? {

        var tormaystulos: Map<Int, Set<Int>> = mutableMapOf()

        // type of street (=street class) decides which volume data we use for trafic (buffering of street width varies)
        if (shouldUseSmallerRadiusVolumes(katuluokkaLuokittelu)) {
            //volumes 15 comparison
            tormaystulos = tormaystarkasteluDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )

        } else if (shouldUseWiderRadiusVolumes(katuluokkaLuokittelu)) {
            //volumes 30 comparison
            tormaystulos = tormaystarkasteluDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        }

        return tormaystulos.values.flatten().max()
    }

    private fun shouldUseWiderRadiusVolumes(katuluokkaLuokittelu: Luokittelutulos?) =
        4 <= katuluokkaLuokittelu?.arvo!!


    private fun shouldUseSmallerRadiusVolumes(katuluokkaLuokittelu: Luokittelutulos?) =
        katuluokkaLuokittelu?.arvo!! in 1..3  //this is range check


    internal fun getPyorailyLuokitteluTulos(hanke: Hanke): Luokittelutulos {

        val hankeGeometriat = hanke.geometriat
        val hankeGeometriatId = hankeGeometriat?.id
        //if no id -> let's get out of here
        if (hankeGeometriatId == null)
            throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val tormaystarkastelutulos = tormaystarkasteluDao.pyorailyreitit(hankeGeometriat)

        if (matchesPriorityCycling(tormaystarkastelutulos)) {
            return Luokittelutulos(
                hankeGeometriatId,
                LuokitteluType.PYORAILYN_PAAREITTI,
                5,
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()
            )
        } else if (matchesMainCycling(tormaystarkastelutulos)) {
            return Luokittelutulos(
                hankeGeometriatId,
                LuokitteluType.PYORAILYN_PAAREITTI,
                4,
                PyorailyTormaysLuokittelu.PAAREITTI.toString()
            )
        } else {
            return Luokittelutulos(
                hankeGeometriatId,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        }
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

    private fun getBussiLuokitteluTulos(hanke: Hanke, rajaArvot: LuokitteluRajaArvot): Luokittelutulos {
        val hankeGeometriat = hanke.geometriat
        val hankeGeometriatId = hankeGeometriat?.id
        //if no id -> let's get out of here
        if (hankeGeometriatId == null)
            throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")
        //TODO: implement bus rules here
        return Luokittelutulos(
            hankeGeometriatId,
            LuokitteluType.BUSSILIIKENNE,
            0,
            BussiTormaysLuokittelu.EI_BUSSILIIKENNETTA.toString()
        )
    }

    private fun getRaitiovaunuLuokitteluTulos(hanke: Hanke): Luokittelutulos {

        val hankeGeometriat = hanke.geometriat
        val hankeGeometriatId = hankeGeometriat?.id
        //if no id -> let's get out of here
        if (hankeGeometriatId == null)
            throw IllegalArgumentException("Hanke.geometriat should be set for hankeid ${hanke.id}")

        val tormaystarkastelutulos = tormaystarkasteluDao.raitiotiet(hankeGeometriat)
        if (tormaystarkastelutulos.isEmpty()) {
            return Luokittelutulos(
                hankeGeometriatId, LuokitteluType.RAITIOVAUNULIIKENNE, 0,
                RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
            )
        }
        //trams have shared lane with cars
        if (matchesSharedLane(tormaystarkastelutulos)) {
            return Luokittelutulos(
                hankeGeometriatId, LuokitteluType.RAITIOVAUNULIIKENNE, 4,
                RaitiovaunuTormaysLuokittelu.JAETTU_KAISTA.toString()
            )
        }
        //own lane for tram
        if (matchesOwnLane(tormaystarkastelutulos)) {
            return Luokittelutulos(
                hankeGeometriatId, LuokitteluType.RAITIOVAUNULIIKENNE, 3,
                RaitiovaunuTormaysLuokittelu.OMA_KAISTA.toString()
            )
        }

        //else (should not have this situation?)
        return Luokittelutulos(
            hankeGeometriatId,
            LuokitteluType.RAITIOVAUNULIIKENNE,
            0,
            RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
        )
    }


    private fun matchesSharedLane(tormaystulos: Map<Int, Set<TormaystarkasteluRaitiotiekaistatyyppi>>): Boolean {
        // if contains any rows with ("mixed")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU)
        }
    }

    private fun matchesOwnLane(tormaystulos: Map<Int, Set<TormaystarkasteluRaitiotiekaistatyyppi>>): Boolean {
        // if contains any rows with ("dedicated")
        return tormaystulos.any { tormaystulosRivi ->
            tormaystulosRivi.value.contains(TormaystarkasteluRaitiotiekaistatyyppi.OMA)
        }
    }
}

