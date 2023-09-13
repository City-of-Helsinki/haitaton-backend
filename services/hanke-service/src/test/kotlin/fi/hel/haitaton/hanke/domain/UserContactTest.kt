package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UserContactTest {

    @Test
    fun `HankeUserContact when valid input returns contact`() {
        assertThat(HankeUserContact.from(TEPPO_TESTI, teppoEmail))
            .isEqualTo(HankeUserContact(TEPPO_TESTI, teppoEmail))
    }

    @ParameterizedTest
    @CsvSource("name,", ",email", "' ',", ",' '")
    fun `HankeUserContact when invalid input returns null`(name: String?, email: String?) {
        assertThat(HankeUserContact.from(name, email)).isNull()
    }

    @Test
    fun `ApplicationUserContact when valid input returns contact`() {
        assertThat(ApplicationUserContact.from(TEPPO_TESTI, teppoEmail, HAKIJA))
            .isEqualTo(ApplicationUserContact(TEPPO_TESTI, teppoEmail, HAKIJA))
    }

    @ParameterizedTest
    @CsvSource("name,", ",email", "' ',", ",' '")
    fun `ApplicationUserContact when invalid input returns null`(name: String?, email: String?) {
        assertThat(ApplicationUserContact.from(name, email, HAKIJA)).isNull()
    }
}
