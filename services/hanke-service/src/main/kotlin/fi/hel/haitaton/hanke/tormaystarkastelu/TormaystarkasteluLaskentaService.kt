package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

interface TormaystarkasteluLaskentaService {

    /**
     * @return null if it can't be calculated; missing required information
     */
    fun calculateTormaystarkastelu(hanke: Hanke): TormaystarkasteluTulos?

}
