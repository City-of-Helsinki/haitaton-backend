package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianHoitajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianhoitajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createCableReportApplicationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.createEntity
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
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(
                    hakijaApplicationContact,
                    suorittajaApplicationContact,
                    asianhoitajaApplicationContact,
                    rakennuttajaApplicationContact
                )
        }

        @Test
        fun `typedContacts when not all types present should return existing as typed`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(hakijaApplicationContact, suorittajaApplicationContact)
        }

        @Test
        fun `typedContacts when omitted present filters out given contact`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact
                )
            val kayttaja = createEntity(sahkoposti = suorittajaApplicationContact.email)

            val result = applicationData.typedContacts(omit = kayttaja.sahkoposti)

            assertThat(result).containsExactlyInAnyOrder(hakijaApplicationContact)
        }

        @Test
        fun `typedContacts when omitted is null does no filtering`() {
            val applicationData =
                createCableReportApplicationData(
                    customerWithContacts = hakijaCustomerContact,
                    contractorWithContacts = suorittajaCustomerContact,
                    representativeWithContacts = asianHoitajaCustomerContact,
                    propertyDeveloperWithContacts = rakennuttajaCustomerContact
                )

            val result = applicationData.typedContacts(omit = null)

            assertThat(result)
                .containsExactlyInAnyOrder(
                    hakijaApplicationContact,
                    suorittajaApplicationContact,
                    asianhoitajaApplicationContact,
                    rakennuttajaApplicationContact
                )
        }
    }
}
