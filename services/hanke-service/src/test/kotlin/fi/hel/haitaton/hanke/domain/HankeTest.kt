package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.format.DateTimeFormatter
import org.junit.jupiter.api.Test

internal class HankeTest {

    @Test
    fun `Hanke alku and loppu calculated from alueet`() {
        val hanke = HankeFactory.create().withHankealue()
        val a = DateFactory.getEndDatetime()
        val b = DateFactory.getEndDatetime().plusMonths(1)
        val c = DateFactory.getEndDatetime().plusMonths(2)
        val d = DateFactory.getEndDatetime().plusMonths(3)
        hanke.alueet[0].haittaAlkuPvm = a
        hanke.alueet[0].haittaLoppuPvm = c
        val hankealue =
            HankealueFactory.create(
                haittaAlkuPvm = b,
                haittaLoppuPvm = d,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.JATKUVA_MELUHAITTA,
                polyHaitta = Polyhaitta.JATKUVA_POLYHAITTA,
                tarinaHaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
            )
        hanke.alueet.add(hankealue)

        val alkuPvm = hanke.alkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE)
        val aFormatted = a.format(DateTimeFormatter.BASIC_ISO_DATE)
        val loppuPvm = hanke.loppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE)
        val dFormatted = d.format(DateTimeFormatter.BASIC_ISO_DATE)
        assertThat(alkuPvm).isEqualTo(aFormatted)
        assertThat(loppuPvm).isEqualTo(dFormatted)
    }
}
