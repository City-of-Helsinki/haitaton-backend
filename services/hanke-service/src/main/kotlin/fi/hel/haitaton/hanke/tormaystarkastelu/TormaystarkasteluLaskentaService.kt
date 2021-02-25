package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke

interface TormaystarkasteluLaskentaService {

    fun calculateTormaystarkastelu(hankeTunnus: String): Hanke

    fun getTormaystarkastelu(hankeTunnus: String): TormaystarkasteluTulos
}
