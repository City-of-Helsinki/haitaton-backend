package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.asJsonResource
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HakemusUpdateRequestTest {

    @Nested
    inner class HasChanges {

        @Test
        fun `returns false when nothing has changed`() {
            val original =
                "/fi/hel/haitaton/hanke/hakemus/cableReportApplicationData-empty.json"
                    .asJsonResource<CableReportApplicationData>()
            val request =
                "/fi/hel/haitaton/hanke/hakemus/hakemusUpdateRequest-empty.json"
                    .asJsonResource<JohtoselvityshakemusUpdateRequest>()

            assertThat(request.hasChanges(original)).isFalse()
        }

        @Test
        fun `returns true when name is added`() {
            val original =
                "/fi/hel/haitaton/hanke/hakemus/cableReportApplicationData-empty.json"
                    .asJsonResource<CableReportApplicationData>()
            val request =
                "/fi/hel/haitaton/hanke/hakemus/hakemusUpdateRequest-with-name.json"
                    .asJsonResource<JohtoselvityshakemusUpdateRequest>()

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when customer is added`() {
            val original =
                "/fi/hel/haitaton/hanke/hakemus/cableReportApplicationData-empty.json"
                    .asJsonResource<CableReportApplicationData>()
            val request =
                "/fi/hel/haitaton/hanke/hakemus/hakemusUpdateRequest-with-customer.json"
                    .asJsonResource<JohtoselvityshakemusUpdateRequest>()

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when new customer contact is added`() {
            val original =
                "/fi/hel/haitaton/hanke/hakemus/cableReportApplicationData-with-customer.json"
                    .asJsonResource<CableReportApplicationData>()
            val request =
                "/fi/hel/haitaton/hanke/hakemus/hakemusUpdateRequest-with-customer-new-contact.json"
                    .asJsonResource<JohtoselvityshakemusUpdateRequest>()

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns false when customer contacts are the same`() {
            val original =
                "/fi/hel/haitaton/hanke/hakemus/cableReportApplicationData-with-customer-new-contact.json"
                    .asJsonResource<CableReportApplicationData>()
            val request =
                "/fi/hel/haitaton/hanke/hakemus/hakemusUpdateRequest-with-customer-new-contact.json"
                    .asJsonResource<JohtoselvityshakemusUpdateRequest>()

            assertThat(request.hasChanges(original)).isFalse()
        }
    }
}
