package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LuokitteluRajaArvotServiceTest {

    val service: LuokitteluRajaArvotServiceHardCoded = LuokitteluRajaArvotServiceHardCoded()

    @ParameterizedTest(name = "{0} days will give 'haitta-ajan kesto' classification of {1}")
    @CsvSource("0,1", "13,1", "14,3", "90,3", "91,5", "180,5")
    fun haittaAjanKesto(days: Int, classficationValue: Int) {
        val actual = service.getHaittaAjanKestoLuokka(days)
        assertThat(actual).isEqualTo(classficationValue)
    }

}