package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InformationRequestTest {

    @Nested
    inner class FromHaitatonFieldName {
        @Test
        fun `returns GEOMETRY if name matches areas without properties`() {
            val result = InformationRequestFieldKey.fromHaitatonFieldName("areas[0]")

            assertThat(result).isEqualTo(InformationRequestFieldKey.GEOMETRY)
        }

        @Test
        fun `returns ATTACHMENT if name matches areas with haittojenhallintasuunnitelma`() {
            val result =
                InformationRequestFieldKey.fromHaitatonFieldName(
                    "areas[0].haittojenhallintasuunnitelma[RAITIOLIIKENNE]"
                )

            assertThat(result).isEqualTo(InformationRequestFieldKey.ATTACHMENT)
        }
    }
}
