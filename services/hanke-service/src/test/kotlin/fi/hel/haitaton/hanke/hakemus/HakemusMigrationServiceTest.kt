package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.hakemus.HakemusMigrationService.Companion.contactWithLeastMissingFields
import fi.hel.haitaton.hanke.hakemus.HakemusMigrationService.Companion.defaultIfNullOrBlank
import fi.hel.haitaton.hanke.hakemus.HakemusMigrationService.Companion.mode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class HakemusMigrationServiceTest {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ClearCustomers {
        @Test
        fun `clears customers for a cable report`() {
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = ApplicationFactory.createCompanyCustomer().withContact(),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact(),
                    propertyDeveloperWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact(),
                    representativeWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact(),
                )

            val response = HakemusMigrationService.clearCustomers(data)

            assertThat(response.customersWithContacts()).isEmpty()
        }

        @Test
        fun `clears customers for an excavation notification`() {
            val data =
                ApplicationFactory.createExcavationNotificationData(
                    customerWithContacts = ApplicationFactory.createCompanyCustomer().withContact(),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact(),
                    propertyDeveloperWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact(),
                    representativeWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact(),
                )

            val response = HakemusMigrationService.clearCustomers(data)

            assertThat(response.customersWithContacts()).isEmpty()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ContactWithLeastMissingFields {
        @Test
        fun `returns null when the contact list is empty`() {
            val response = listOf<Contact>().contactWithLeastMissingFields()

            assertThat(response).isNull()
        }

        @Test
        fun `returns the first element when the list has one element`() {
            val contacts = listOf(ApplicationFactory.createContact())

            val response = contacts.contactWithLeastMissingFields()

            assertThat(response).isNotNull().isSameInstanceAs(contacts[0])
        }

        @Test
        fun `returns the first element when the list has several elements with all fields`() {
            val contacts =
                listOf(
                    ApplicationFactory.createContact(firstName = "Teppo"),
                    ApplicationFactory.createContact(firstName = "Pekka"),
                    ApplicationFactory.createContact(firstName = "Matti"),
                    ApplicationFactory.createContact(firstName = "Hannu")
                )

            val response = contacts.contactWithLeastMissingFields()

            assertThat(response).isNotNull().isSameInstanceAs(contacts[0])
        }

        @Test
        fun `returns the element with the least missing values`() {
            val contacts =
                listOf(
                    ApplicationFactory.createContact(firstName = null, lastName = "", phone = " "),
                    ApplicationFactory.createContact(firstName = "Pekka", lastName = " "),
                    ApplicationFactory.createContact(firstName = "Matti", email = "", phone = null),
                    ApplicationFactory.createContact(
                        firstName = "Hannu",
                        lastName = null,
                        email = " "
                    )
                )

            val response = contacts.contactWithLeastMissingFields()

            assertThat(response).isNotNull().isSameInstanceAs(contacts[1])
        }
    }

    @Nested
    inner class FindOrderer {
        @Test
        fun `returns null when there are no contacts`() {
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = null,
                    contractorWithContacts = null
                )

            val result = HakemusMigrationService.findOrderer(data)

            assertThat(result).isNull()
        }

        @Test
        fun `returns the orderer when there is just one`() {
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts = ApplicationFactory.createCompanyCustomerWithOrderer(),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContact()
                )

            val result = HakemusMigrationService.findOrderer(data)

            assertThat(result).isSameInstanceAs(data.customerWithContacts!!.contacts[0])
        }

        @Test
        fun `returns the orderer with the least missing fields if there are several`() {
            val nonOrderer =
                ApplicationFactory.createContact(
                    orderer = false,
                    firstName = null,
                    lastName = "",
                    phone = " "
                )
            val missingTwo =
                ApplicationFactory.createContact(
                    orderer = true,
                    firstName = "Pekka",
                    lastName = " ",
                    email = ""
                )
            val firstMissingOne =
                ApplicationFactory.createContact(orderer = true, firstName = "Matti", phone = null)
            val secondMissingOne =
                ApplicationFactory.createContact(orderer = true, firstName = "Hannu", email = " ")
            val contacts = arrayOf(nonOrderer, missingTwo, firstMissingOne, secondMissingOne)
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContacts(*contacts),
                )

            val result = HakemusMigrationService.findOrderer(data)

            assertThat(result).isSameInstanceAs(firstMissingOne)
        }
    }

    @Nested
    inner class DefaultIfNullOrBlank {

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        @NullSource
        fun `returns '-' when the value is null or blank`(value: String?) {
            assertThat(value.defaultIfNullOrBlank()).isEqualTo("-")
        }

        @ParameterizedTest
        @ValueSource(strings = [" f ", "first", "-"])
        fun `returns the value when the value is not null nor blank`(value: String) {
            assertThat(value.defaultIfNullOrBlank()).isEqualTo(value)
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class Mode {
        @Test
        fun `returns null when list is empty`() {
            val list = listOf<String>()

            val mode = list.mode()

            assertThat(mode).isNull()
        }

        @Test
        fun `returns null when list is all blanks`() {
            val list = listOf("", " ", "   ")

            val mode = list.mode()

            assertThat(mode).isNull()
        }

        @ParameterizedTest
        @MethodSource("modeTests")
        fun `returns the mode of the list`(list: List<String>, expected: String) {
            assertThat(list.mode()).isEqualTo(expected)
        }

        private fun modeTests() =
            listOf(
                Arguments.of(listOf("first"), "first"),
                Arguments.of(listOf("first", "second"), "first"),
                Arguments.of(listOf("first", "second", "second"), "second"),
                Arguments.of(listOf("first", "second", "first"), "first"),
                Arguments.of(listOf("first", "second", "third", "second", "first"), "first"),
                Arguments.of(listOf("first", "second", "third", "second", "third"), "second"),
                Arguments.of(listOf(" ", "first", " "), "first"),
            )
    }
}
