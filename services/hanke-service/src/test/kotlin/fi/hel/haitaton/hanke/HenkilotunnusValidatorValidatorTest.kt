package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.validation.HenkilotunnusValidator.isValidHenkilotunnus
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HenkilotunnusValidatorValidatorTest {

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "190950+016X", // First valid year of 1800s
                "200299+703R", // Last year of 1800s
                "180800V5272", // First year of 1900s
                "090626-885W", // Classic separator of 1900s
                "230577U869S", // The different separators of 1900s
                "260570V398P",
                "080720W894T",
                "140288X700X",
                "240411Y748N",
                "110699W216E", // The last year of 1900s
                "230500D7546", // First year of 2000s
                "120621A3731", // Classic separator of 2000s
                "120413B621B", // The different separators of 2000s
                "130723C4416",
                "241003D605U",
                "100703E622X",
                "060623F6387",
                "180524E0426", // As of writing, the last valid year of 2000s
                "290204A000B", // Leap day on a leap year
                "110166-8080", // Zero as check digit
            ])
    fun `accepts valid henkilotunnus`(hetu: String) {
        assertThat(hetu.isValidHenkilotunnus()).isTrue()
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "260570V398", // Missing check digit
                "080720W84T", // Missing one index digit
                "140288700X", // Missing separator
                "24041Y748N", // Missing one date digit
                "080720W8942T", // Extra digit
                "190949+016N", // Before 1850
                "120333B054D", // In the future
                "120621K3731", // Invalid separator
                "260570V398I", // Invalid check digits
                "080720W8944",
                "241003D605D",
                "100703E6220",
                "290204A000K",
                "290203A0003", // Leap day in a non-leap year
            ])
    fun `rejects invalid henkilotunnus`(hetu: String) {
        assertThat(hetu.isValidHenkilotunnus()).isFalse()
    }
}
