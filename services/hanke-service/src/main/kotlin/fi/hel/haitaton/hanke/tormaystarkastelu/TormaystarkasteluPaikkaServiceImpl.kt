package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke
import java.util.function.Consumer

class TormaystarkasteluPaikkaServiceImpl : TormaystarkasteluPaikkaService {

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

        val tormaystarkastelutulos = ""   //TODO: call dao check to get the priority/main

        if (matchesPriorityCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "5", PyorailyTormaysLuokittelu.FIVE.toString()), )

        } else if (matchesMainCycling(tormaystarkastelutulos)) {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "4", PyorailyTormaysLuokittelu.FOUR.toString()), )

        } else {
            pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "0", PyorailyTormaysLuokittelu.ZERO.toString()), )
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

