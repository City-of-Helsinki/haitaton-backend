package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

interface TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos list for hanke based on its hankeGeometria comparison to the different map references
     * and rajaarvot which is brought in for some classification information
     */
    fun getTormaystarkasteluLuokitteluTulos(hanke: Hanke, rajaarvot: LuokitteluRajaarvot) : List<Luokittelutulos>
}