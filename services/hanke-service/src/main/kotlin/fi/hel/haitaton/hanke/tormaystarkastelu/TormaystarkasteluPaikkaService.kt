package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

interface TormaystarkasteluPaikkaService {

    /**
     * Returns luokittelutulos map for hanke based on its hankeGeometria comparison to the different map references
     * and rajaarvot which is brought in for some classification information
     */
    fun calculateTormaystarkasteluLuokitteluTulos(
        hanke: Hanke,
        rajaArvot: LuokitteluRajaArvot
    ): Map<LuokitteluType, Luokittelutulos>
}
