package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UserContactTest {

    @Nested
    inner class HankeUserContacts {

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
    }

    @Nested
    inner class ApplicationUserContacts {

        @Test
        fun `ApplicationUserContact when valid input returns contact`() {
            assertThat(ApplicationUserContact.from(TEPPO_TESTI, teppoEmail, HAKIJA))
                .isEqualTo(ApplicationUserContact(TEPPO_TESTI, teppoEmail, HAKIJA))
        }

        @ParameterizedTest
        @CsvSource("name,", ",email", "' ',", ",' '")
        fun `ApplicationUserContact when invalid input returns null`(
            name: String?,
            email: String?
        ) {
            assertThat(ApplicationUserContact.from(name, email, HAKIJA)).isNull()
        }

        @Test
        fun `typedContacts when all types are present should return all as typed`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    customerWithContacts = AlluDataFactory.hakijaCustomerContact,
                    contractorWithContacts = AlluDataFactory.suorittajaCustomerContact,
                    representativeWithContacts = AlluDataFactory.asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = AlluDataFactory.rakennuttajaCustomerContact
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(
                    AlluDataFactory.hakijaApplicationContact,
                    AlluDataFactory.suorittajaApplicationContact,
                    AlluDataFactory.asianhoitajaApplicationContact,
                    AlluDataFactory.rakennuttajaApplicationContact
                )
        }

        @Test
        fun `typedContacts when not all types present should return existing as typed`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    customerWithContacts = AlluDataFactory.hakijaCustomerContact,
                    contractorWithContacts = AlluDataFactory.suorittajaCustomerContact
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(
                    AlluDataFactory.hakijaApplicationContact,
                    AlluDataFactory.suorittajaApplicationContact
                )
        }

        @Test
        fun `removeInviter when inviter present filters inviter from contacts`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    customerWithContacts = AlluDataFactory.hakijaCustomerContact,
                    contractorWithContacts = AlluDataFactory.suorittajaCustomerContact
                )
            val inviter =
                HankeKayttajaFactory.createEntity(
                    sahkoposti = AlluDataFactory.suorittajaApplicationContact.email
                )

            val result = applicationData.typedContacts(omit = inviter.sahkoposti)

            assertThat(result).containsExactlyInAnyOrder(AlluDataFactory.hakijaApplicationContact)
        }

        @Test
        fun `removeInviter when inviter is null does no filtering`() {
            val applicationData =
                AlluDataFactory.createCableReportApplicationData(
                    customerWithContacts = AlluDataFactory.hakijaCustomerContact,
                    contractorWithContacts = AlluDataFactory.suorittajaCustomerContact,
                    representativeWithContacts = AlluDataFactory.asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = AlluDataFactory.rakennuttajaCustomerContact
                )

            val result = applicationData.typedContacts(omit = null)

            assertThat(result)
                .containsExactlyInAnyOrder(
                    AlluDataFactory.hakijaApplicationContact,
                    AlluDataFactory.suorittajaApplicationContact,
                    AlluDataFactory.asianhoitajaApplicationContact,
                    AlluDataFactory.rakennuttajaApplicationContact
                )
        }
    }
}
