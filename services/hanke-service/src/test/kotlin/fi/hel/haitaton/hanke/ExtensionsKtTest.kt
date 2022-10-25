package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test

internal class ExtensionsKtTest {

    @Test
    fun floatRoundToOneDecimal() {
        assertThat(3.141592f.roundToOneDecimal()).isEqualTo(3.1f)
        // Make sure the rounding actually does something, e.g. 3.4999999f == 3.5f
        assertThat(3.499999f).isLessThan(3.5f)
        assertThat(3.500001f).isGreaterThan(3.5f)
        assertThat(3.499999f.roundToOneDecimal()).isEqualTo(3.5f)
        assertThat(3.500001f.roundToOneDecimal()).isEqualTo(3.5f)
        assertThat(0.0000009f.roundToOneDecimal()).isEqualTo(0.0f)
        assertThat(0.0499999f.roundToOneDecimal()).isEqualTo(0.0f)
        assertThat(0.19999f.roundToOneDecimal()).isEqualTo(0.2f)
    }
}
