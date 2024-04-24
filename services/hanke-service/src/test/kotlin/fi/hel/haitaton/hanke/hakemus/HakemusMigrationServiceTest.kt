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
            val missingThree =
                Contact(firstName = null, lastName = "", email = "some@email", phone = " ")
            val missingOne =
                Contact(firstName = "Pekka", lastName = " ", email = "pekka@email", phone = "123")
            val firstMissingTwo =
                Contact(firstName = "Matti", lastName = "Mattila", email = "", phone = null)
            val secondMissingTwo =
                Contact(firstName = "Hannu", lastName = null, email = " ", phone = "321")
            val contacts = listOf(missingThree, missingOne, firstMissingTwo, secondMissingTwo)

            val response = contacts.contactWithLeastMissingFields()

            assertThat(response).isNotNull().isSameInstanceAs(missingOne)
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
                Contact(
                    firstName = ApplicationFactory.TEPPO,
                    lastName = ApplicationFactory.TESTIHENKILO,
                    email = ApplicationFactory.TEPPO_EMAIL,
                    phone = ApplicationFactory.TEPPO_PHONE,
                    orderer = false
                )
            val missingTwo =
                Contact(
                    firstName = "Pekka",
                    lastName = " ",
                    email = "",
                    phone = "123",
                    orderer = true
                )
            val firstMissingOne =
                Contact(
                    firstName = "Matti",
                    lastName = "Mattila",
                    email = "matti@email",
                    phone = null,
                    orderer = true
                )
            val secondMissingOne =
                Contact(
                    firstName = "Hannu",
                    lastName = "Hannula",
                    email = " ",
                    phone = "321",
                    orderer = true
                )
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
