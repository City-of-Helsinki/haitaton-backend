package fi.hel.haitaton.hanke.permissions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UserContactTest {

    @Test
    fun `UserContact when valid input returns contact`() {
        assertThat(UserContact.from(TEPPO_TESTI, teppoEmail))
            .isEqualTo(UserContact(TEPPO_TESTI, teppoEmail))
    }

    @ParameterizedTest
    @CsvSource("name,", ",email", "' ',", ",' '")
    fun `UserContact when invalid input returns null`(name: String?, email: String?) {
        assertThat(UserContact.from(name, email)).isNull()
    }
}
