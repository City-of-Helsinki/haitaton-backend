package fi.hel.haitaton.hanke.application

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val DUMMY_EMAIL = "dummymail@mail.com"
private const val DUMMY_PHONE = "04012345678"

class ContactTest {

    @ParameterizedTest
    @CsvSource(
        "Matti,Meikalainen,Matti Meikalainen",
        "'',Meikalainen,Meikalainen",
        "Matti,'',Matti",
        "'','',''"
    )
    fun `fullName concatenates first and last names`(
        firstName: String,
        lastName: String,
        expectedResult: String
    ) {
        val contact = Contact(firstName, lastName, DUMMY_EMAIL, DUMMY_PHONE)
        assertThat(contact.fullName()).isEqualTo(expectedResult)
    }

    @Test
    fun `fullName when firstName null should provide lastName`() {
        val contact =
            Contact(firstName = null, lastName = "Last", email = DUMMY_EMAIL, phone = DUMMY_PHONE)
        assertThat(contact.fullName()).isEqualTo("Last")
    }

    @Test
    fun `fullName when lastName null should provide firstName`() {
        val contact =
            Contact(firstName = "First", lastName = null, email = DUMMY_EMAIL, phone = DUMMY_PHONE)
        assertThat(contact.fullName()).isEqualTo("First")
    }

    @Test
    fun `fullName when both names null should provide null`() {
        val contact =
            Contact(firstName = null, lastName = null, email = DUMMY_EMAIL, phone = DUMMY_PHONE)
        assertThat(contact.fullName()).isNull()
    }

    @Test
    fun `findOrderer when orderer contact exists should return it`() {
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                representativeWithContacts =
                    AlluDataFactory.createCompanyCustomer().withContacts(createContact()),
                propertyDeveloperWithContacts =
                    AlluDataFactory.createCompanyCustomer().withContacts(createContact()),
            )

        val result = applicationData.findOrderer()

        val allContacts = applicationData.customersWithContacts().flatMap { it.contacts }
        assertThat(allContacts).hasSize(4)
        val expectedResult =
            Contact(
                firstName = "Teppo",
                lastName = "Testihenkilö",
                email = "teppo@example.test",
                phone = "04012345678",
                orderer = true,
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `findOrderer when no orderer contact exists should return null`() {
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer().withContacts(createContact())
            )

        val result = applicationData.findOrderer()

        val allContacts = applicationData.customersWithContacts().flatMap { it.contacts }
        assertThat(allContacts).hasSize(2)
        assertThat(result).isNull()
    }
}
