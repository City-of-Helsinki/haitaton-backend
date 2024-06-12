package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AutoliikenneluokitteluTest {

    @ParameterizedTest(name = "Autoliikenneindeksi should be {5}")
    @CsvSource("1,1,1,1,1,1.0", "1,2,2,2,2,1.9", "3,3,3,3,3,3.0", "3,4,4,4,4,3.9", "5,5,5,5,5,5.0")
    fun `calculate index`(
        haittaAjanKesto: Int,
        katuluokka: Int,
        liikennemaara: Int,
        kaistahaitta: Int,
        kaistapituushaitta: Int,
        indeksi: Float,
    ) {
        val luokittelu =
            Autoliikenneluokittelu(
                haittaAjanKesto,
                katuluokka,
                liikennemaara,
                kaistahaitta,
                kaistapituushaitta,
            )
        assertThat(luokittelu.indeksi).isEqualTo(indeksi)
    }
}
