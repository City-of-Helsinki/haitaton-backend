package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.util.UUID
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
        val id = UUID.fromString("4f5cbacd-5f22-45d8-8658-3921a454085d")
        Yhteyshenkilo(id, firstName, lastName, "dummymail", "04012345678").let {
            assertThat(it.fullName()).isEqualTo(expectedResult)
        }
    }
}
