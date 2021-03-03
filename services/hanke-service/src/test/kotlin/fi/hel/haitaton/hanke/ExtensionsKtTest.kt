package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

internal class ExtensionsKtTest {

    @Test
    fun floatRound() {
        val value = 3.14159f
        assertThat(value.round(3)).isEqualTo(3.142f)
        assertThat(value.round(2)).isEqualTo(3.14f)
        assertThat(value.round(1)).isEqualTo(3.1f)
    }
}
