package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

class TormaystarkasteluPaikkaServiceImpl : TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaarvot which is brought in for some classification information
     */
    override fun getTormaystarkasteluLuokitteluTulos(hanke: Hanke, rajaarvot: LuokitteluRajaarvot): List<Luokittelutulos> {

        val luokitteluTulosComplete = mutableListOf<Luokittelutulos>()

        luokitteluTulosComplete.addAll(getPyorailyLuokitteluTulos(hanke, rajaarvot))
        luokitteluTulosComplete.addAll(getKatuluokkaLuokitteluTulos(hanke, rajaarvot))
        luokitteluTulosComplete.addAll(getLiikennemaaraLuokitteluTulos(hanke, rajaarvot))

        //TODO: "call methods for deciding separate luokittelu steps for missing luokittelu"
        //bussit
        //raitiovaunut

        return luokitteluTulosComplete
    }

    internal fun getPyorailyLuokitteluTulos(hanke: Hanke, rajaarvot: LuokitteluRajaarvot): List<Luokittelutulos> {
        TODO("Not yet implemented")
    }

    internal fun getLiikennemaaraLuokitteluTulos(hanke: Hanke, rajaarvot: LuokitteluRajaarvot): List<Luokittelutulos> {
        TODO("Not yet implemented")
    }

    internal fun getKatuluokkaLuokitteluTulos(hanke: Hanke, rajaarvot: LuokitteluRajaarvot): List<Luokittelutulos> {
        TODO("Not yet implemented")
    }

}

