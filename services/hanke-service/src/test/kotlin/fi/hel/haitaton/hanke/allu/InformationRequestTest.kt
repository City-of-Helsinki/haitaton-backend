package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class InformationRequestTest {

    @Nested
    inner class FromHaitatonFieldName {

        @Test
        fun `returns GEOMETRY if name matches areas without properties in johtoselvityshakemus`() {
            val result =
                InformationRequestFieldKey.fromHaitatonFieldName(
                    "areas[0]",
                    ApplicationType.CABLE_REPORT,
                )

            assertThat(result).isEqualTo(InformationRequestFieldKey.GEOMETRY)
        }

        @Test
        fun `returns GEOMETRY if name matches work areas`() {
            val result =
                InformationRequestFieldKey.fromHaitatonFieldName(
                    "areas[0].tyoalueet[0]",
                    ApplicationType.EXCAVATION_NOTIFICATION,
                )

            assertThat(result).isEqualTo(InformationRequestFieldKey.GEOMETRY)
        }

        @Test
        fun `returns ATTACHMENT if name matches areas with haittojenhallintasuunnitelma`() {
            val result =
                InformationRequestFieldKey.fromHaitatonFieldName(
                    "areas[0].haittojenhallintasuunnitelma[RAITIOLIIKENNE]",
                    ApplicationType.EXCAVATION_NOTIFICATION,
                )

            assertThat(result).isEqualTo(InformationRequestFieldKey.ATTACHMENT)
        }
    }
}
