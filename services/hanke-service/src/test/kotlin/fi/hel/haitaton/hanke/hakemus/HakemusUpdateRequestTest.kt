package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HakemusUpdateRequestTest {

    @Nested
    inner class HasChanges {

        @Test
        fun `returns false when nothing has changed`() {
            val original =
                ApplicationFactory.createApplicationEntity(
                    applicationData = ApplicationFactory.createBlankCableReportApplicationData(),
                    hanke = HankeFactory.createMinimalEntity()
                )
            val request = HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest()

            assertThat(request.hasChanges(original)).isFalse()
        }

        @Test
        fun `returns true when name is changed`() {
            val original =
                ApplicationFactory.createApplicationEntity(
                    applicationData = ApplicationFactory.createBlankCableReportApplicationData(),
                    hanke = HankeFactory.createMinimalEntity()
                )
            val request =
                HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest()
                    .copy(name = "Testihakemus")

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when work description is changed`() {
            val original =
                ApplicationFactory.createApplicationEntity(
                    applicationData = ApplicationFactory.createBlankCableReportApplicationData(),
                    hanke = HankeFactory.createMinimalEntity()
                )
            val request =
                HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest()
                    .copy(
                        workDescription = "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                    )

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when a customer is added`() {
            val original =
                ApplicationFactory.createApplicationEntity(
                    applicationData = ApplicationFactory.createBlankCableReportApplicationData(),
                    hanke = HankeFactory.createMinimalEntity()
                )
            val request =
                createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
                    "cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f"
                )

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns true when new customer contact is added`() {
            val original =
                createApplicationEntityWithYhteystiedot(
                    Pair("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f", true)
                )
            val request =
                createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
                    "cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f",
                    "3047a6fc-5a2b-41cb-bb99-1f907fef2101"
                )

            assertThat(request.hasChanges(original)).isTrue()
        }

        @Test
        fun `returns false when customer contacts are the same`() {
            val original =
                createApplicationEntityWithYhteystiedot(
                    Pair("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f", true),
                    Pair("3047a6fc-5a2b-41cb-bb99-1f907fef2101", false)
                )
            val request =
                createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
                    "cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f",
                    "3047a6fc-5a2b-41cb-bb99-1f907fef2101"
                )

            assertThat(request.hasChanges(original)).isFalse()
        }

        @Test
        fun `returns true when customer contacts are removed`() {
            val original =
                createApplicationEntityWithYhteystiedot(
                    Pair("cd1d4d2f-526b-4ee5-a1fa-97b14d25a11f", true),
                    Pair("3047a6fc-5a2b-41cb-bb99-1f907fef2101", false)
                )
            val request = createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts()

            assertThat(request.hasChanges(original)).isTrue()
        }
    }

    private fun createApplicationEntityWithYhteystiedot(
        vararg hakemusyhteyshenkilot: Pair<String, Boolean>
    ): ApplicationEntity =
        ApplicationFactory.createApplicationEntity(
                applicationData = ApplicationFactory.createBlankCableReportApplicationData(),
                hanke = HankeFactory.createMinimalEntity()
            )
            .apply {
                yhteystiedot[ApplicationContactType.HAKIJA] =
                    HakemusyhteystietoFactory.createEntity(
                            tyyppi = CustomerType.COMPANY,
                            rooli = ApplicationContactType.HAKIJA,
                            nimi = "Testiyritys",
                            sahkoposti = "info@testiyritys.fi",
                            puhelinnumero = "0401234567",
                            ytunnus = "1234567-8",
                            application = this
                        )
                        .apply {
                            yhteyshenkilot =
                                hakemusyhteyshenkilot.map {
                                    HakemusyhteyshenkiloFactory.createEntity(
                                        this,
                                        HankeKayttajaFactory.createEntity(
                                            id = UUID.fromString(it.first)
                                        ),
                                        it.second
                                    )
                                }
                        }
            }

    private fun createJohtoselvityshakemusUpdateRequestWithCustomerWithContacts(
        vararg yhteyshenkilot: String
    ): JohtoselvityshakemusUpdateRequest =
        HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest()
            .copy(
                customerWithContacts =
                    CustomerWithContactsRequest(
                        CustomerRequest(
                            type = CustomerType.COMPANY,
                            name = "Testiyritys",
                            email = "info@testiyritys.fi",
                            phone = "0401234567",
                            registryKey = "1234567-8"
                        ),
                        yhteyshenkilot.map { ContactRequest(UUID.fromString(it)) }
                    )
            )
}
