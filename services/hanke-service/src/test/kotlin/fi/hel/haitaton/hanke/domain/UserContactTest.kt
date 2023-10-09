package fi.hel.haitaton.hanke.domain

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianHoitajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianhoitajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaApplicationContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
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
            val applicationData = cableReport()

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
                cableReport(
                    customer = hakijaCustomerContact,
                    contractor = suorittajaCustomerContact,
                    representative = null,
                    developer = null
                )

            val result = applicationData.typedContacts()

            assertThat(result)
                .containsExactlyInAnyOrder(hakijaApplicationContact, suorittajaApplicationContact)
        }

        @Test
        fun `typedContacts when omitted present filters out given contact`() {
            val applicationData =
                cableReport(
                    customer = hakijaCustomerContact,
                    contractor = suorittajaCustomerContact,
                    representative = null,
                    developer = null
                )

            val result = applicationData.typedContacts(omit = suorittajaApplicationContact.email)

            assertThat(result).containsExactlyInAnyOrder(hakijaApplicationContact)
        }

        @Test
        fun `typedContacts when omitted is null does no filtering`() {
            val applicationData = cableReport()

            val result = applicationData.typedContacts(omit = null)

            assertThat(result)
                .containsExactlyInAnyOrder(
                    hakijaApplicationContact,
                    suorittajaApplicationContact,
                    asianhoitajaApplicationContact,
                    rakennuttajaApplicationContact
                )
        }

        @Test
        fun `subtractByEmail when same data should return empty set`() {
            val oldContacts = cableReport().typedContacts()
            val updatedContacts = cableReport().typedContacts()

            val result = updatedContacts.subtractByEmail(oldContacts)

            assertThat(result).isEmpty()
        }

        @Test
        fun `subtractByEmail when customer changes and has new contact should return the new contact`() {
            val oldContacts = cableReport().typedContacts()
            val updatedContacts = cableReport(contractor = person()).typedContacts()

            val result = updatedContacts.subtractByEmail(oldContacts)

            assertThat(result).hasSize(1)
            assertThat(result.first()).all {
                prop(ApplicationUserContact::name).isEqualTo(TEPPO_TESTI)
                prop(ApplicationUserContact::email).isEqualTo(teppoEmail)
                prop(ApplicationUserContact::type).isEqualTo(TYON_SUORITTAJA)
            }
        }

        @Test
        fun `subtractByEmail when customer changes but contact email is same should return empty set`() {
            val oldApplication = cableReport(representative = company())
            val updatedApplication = cableReport(representative = person())

            val result =
                updatedApplication.typedContacts().subtractByEmail(oldApplication.typedContacts())

            assertThat(updatedApplication.customersWithContacts())
                .isNotEqualTo(oldApplication.customersWithContacts())
            assertThat(result).isEmpty()
        }

        @Test
        fun `subtractByEmail when customer has added new contact should return the new contact`() {
            val oldApplication = cableReport()
            val updatedApplication =
                cableReport(developer = oldApplication.propertyDeveloperWithContacts.plusContact())

            val result =
                updatedApplication.typedContacts().subtractByEmail(oldApplication.typedContacts())

            assertThat(result).hasSize(1)
            assertThat(result.first()).all {
                prop(ApplicationUserContact::name).isEqualTo(TEPPO_TESTI)
                prop(ApplicationUserContact::email).isEqualTo(teppoEmail)
                prop(ApplicationUserContact::type).isEqualTo(RAKENNUTTAJA)
            }
        }

        @Test
        fun `subtractByEmail when customer several new contacts should return them`() {
            val oldApplication = cableReport(representative = null)
            val updatedApplication =
                cableReport(
                    representative =
                        AlluDataFactory.createCompanyCustomer()
                            .withContacts(
                                createContact(email = "first"),
                                createContact(email = "second"),
                                createContact(email = "third")
                            )
                )

            val result =
                updatedApplication.typedContacts().subtractByEmail(oldApplication.typedContacts())

            assertThat(result).hasSize(3)
            assertThat(result)
                .extracting { it.email }
                .containsExactlyInAnyOrder("first", "second", "third")
        }
    }

    private fun person() = AlluDataFactory.createPersonCustomer().withContact()

    private fun company() = AlluDataFactory.createCompanyCustomer().withContact()

    private fun cableReport(
        customer: CustomerWithContacts = hakijaCustomerContact,
        contractor: CustomerWithContacts = suorittajaCustomerContact,
        representative: CustomerWithContacts? = asianHoitajaCustomerContact,
        developer: CustomerWithContacts? = rakennuttajaCustomerContact
    ) =
        AlluDataFactory.createCableReportApplicationData(
            customerWithContacts = customer,
            contractorWithContacts = contractor,
            representativeWithContacts = representative,
            propertyDeveloperWithContacts = developer
        )

    private fun CustomerWithContacts?.plusContact(contact: Contact = createContact()) =
        this?.copy(contacts = contacts.plus(contact))
}
