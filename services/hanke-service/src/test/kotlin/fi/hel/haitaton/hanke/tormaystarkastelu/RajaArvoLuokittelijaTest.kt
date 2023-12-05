package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RajaArvoLuokittelijaTest {

    @ParameterizedTest(name = "{0} days will give 'haitta-ajan kesto' classification of {1}")
    @CsvSource(
        "-1,0",
        "0,1",
        "13,1",
        "14,3",
        "90,3",
        "91,5",
        "180,5",
    )
    fun haittaAjanKesto(days: Int, classificationValue: Int) {
        val actual = RajaArvoLuokittelija.haittaajankestoluokka(days)
        assertThat(actual).isEqualTo(classificationValue)
    }

    @ParameterizedTest(name = "{0} cars will give 'liikennemaara' classification of {1}")
    @CsvSource(
        "-140,0",
        "0,0",
        "1,1",
        "499,1",
        "500,2",
        "1499,2",
        "1500,3",
        "4999,3",
        "5000,4",
        "9999,4",
        "10000,5",
        "18000,5",
    )
    fun liikennemaara(volume: Int, classificationValue: Int) {
        val actual = RajaArvoLuokittelija.liikennemaaraluokka(volume)
        assertThat(actual).isEqualTo(classificationValue)
    }

    @ParameterizedTest(name = "{0} buses will give 'bussiliikenne' classification of {1}")
    @CsvSource(
        "-1000,0",
        "-1,0",
        "0,2",
        "4,2",
        "5,3",
        "10,3",
        "11,4",
        "20,4",
        "21,5",
        "100,5",
    )
    fun getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses: Int, classificationValue: Int) {
        val actual = RajaArvoLuokittelija.linjaautoliikenteenRuuhkavuoroluokka(countOfRushHourBuses)
        assertThat(actual).isEqualTo(classificationValue)
    }
}
