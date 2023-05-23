package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class YhteyshenkiloTest {

    @ParameterizedTest
    @CsvSource(
        "Matti,Meikalainen,Matti Meikalainen",
        "'',Meikalainen,Meikalainen",
        "Matti,'',Matti",
        "'','',''"
    )
    fun `wholeName concatenates first and last names`(
        firstName: String,
        lastName: String,
        expectedResult: String
    ) {
        Yhteyshenkilo(firstName, lastName, "dummymail", "04012345678").let {
            assertThat(it.fullName()).isEqualTo(expectedResult)
        }
    }
}
