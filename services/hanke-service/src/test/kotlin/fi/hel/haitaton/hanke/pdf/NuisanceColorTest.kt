package fi.hel.haitaton.hanke.pdf

import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NuisanceColorTest {

    @Nested
    inner class SelectColorStyle {
        @Test
        fun `returns blue when parameter is null`() {
            val result = NuisanceColor.selectColor(null)

            assertThat(result).isSameInstanceAs(NuisanceColor.BLUE)
        }

        @Test
        fun `returns blue when parameter is Not a Number`() {
            val result = NuisanceColor.selectColor(Float.NaN)

            assertThat(result).isSameInstanceAs(NuisanceColor.BLUE)
        }

        @Test
        fun `returns blue when parameter is negative`() {
            val result = NuisanceColor.selectColor(-0.001f)

            assertThat(result).isSameInstanceAs(NuisanceColor.BLUE)
        }

        @Test
        fun `returns gray when index is zero`() {
            val result = NuisanceColor.selectColor(0f)

            assertThat(result).isSameInstanceAs(NuisanceColor.GRAY)
        }

        @Test
        fun `returns green when index is just above zero`() {
            val result = NuisanceColor.selectColor(0.001f)

            assertThat(result).isSameInstanceAs(NuisanceColor.GREEN)
        }

        @Test
        fun `returns green when index is just below three`() {
            val result = NuisanceColor.selectColor(2.99f)

            assertThat(result).isSameInstanceAs(NuisanceColor.GREEN)
        }

        @Test
        fun `returns yellow when index is exactly three`() {
            val result = NuisanceColor.selectColor(3f)

            assertThat(result).isSameInstanceAs(NuisanceColor.YELLOW)
        }

        @Test
        fun `returns yellow when index is just below four`() {
            val result = NuisanceColor.selectColor(3.999f)

            assertThat(result).isSameInstanceAs(NuisanceColor.YELLOW)
        }

        @Test
        fun `returns red when index is exactly four`() {
            val result = NuisanceColor.selectColor(4.0f)

            assertThat(result).isSameInstanceAs(NuisanceColor.RED)
        }

        @Test
        fun `returns red when index is 5`() {
            val result = NuisanceColor.selectColor(5.0f)

            assertThat(result).isSameInstanceAs(NuisanceColor.RED)
        }

        @Test
        fun `returns red when index is way above five`() {
            val result = NuisanceColor.selectColor(999f)

            assertThat(result).isSameInstanceAs(NuisanceColor.RED)
        }
    }
}
