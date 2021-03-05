package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

internal class ExtensionsKtTest {

    @Test
    fun floatRoundToOneDecimal() {
        assertThat(3.1415926f.roundToOneDecimal()).isEqualTo(3.1f)
        assertThat(3.4999999f.roundToOneDecimal()).isEqualTo(3.5f)
        assertThat(3.5000001f.roundToOneDecimal()).isEqualTo(3.5f)
        assertThat(0.0000009f.roundToOneDecimal()).isEqualTo(0.0f)
        assertThat(0.0499999f.roundToOneDecimal()).isEqualTo(0.0f)
        assertThat(0.19999f.roundToOneDecimal()).isEqualTo(0.2f)
    }
}
