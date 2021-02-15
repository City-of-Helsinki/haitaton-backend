package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

open class TormaystarkasteluPaikkaServiceImpl(private val dao: TormaystarkasteluDao) : TormaystarkasteluPaikkaService {

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
        val hankeGeometriat = hanke.geometriat ?: throw IllegalStateException("Hanke.geometriat must be set")
        val tormaystarkastelutulos = dao.pyorailyreitit(hankeGeometriat)
        val uniqueResults = tormaystarkastelutulos.values.flatten().distinct()
        when {
            uniqueResults.contains(TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI) -> {
                //pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "5", PyorailyTormaysLuokittelu.FIVE.toString()), )
            }
            uniqueResults.contains(TormaystarkasteluPyorailyreittiluokka.PAAREITTI) -> {
                //pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "4", PyorailyTormaysLuokittelu.FOUR.toString()), )
            }
            else -> {
                //pyorailyLuokittelu.add(Luokittelutulos(hankeGeometriatId, LuokitteluType.PYORAILYN_PAAREITTI, "0", PyorailyTormaysLuokittelu.ZERO.toString()), )
            }
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

