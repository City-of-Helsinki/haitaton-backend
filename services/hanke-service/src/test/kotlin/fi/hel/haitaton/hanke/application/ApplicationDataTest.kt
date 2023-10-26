package fi.hel.haitaton.hanke.application

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.UserContactFactory.asianhoitajaContact
import fi.hel.haitaton.hanke.factory.UserContactFactory.hakijaContact
import fi.hel.haitaton.hanke.factory.UserContactFactory.rakennuttajaContact
import fi.hel.haitaton.hanke.factory.UserContactFactory.suorittajaContact
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class ApplicationDataTest {

    @Test
    fun `contactPersonEmails when all types are present should return all`() {
        val applicationData = cableReport()

        val result = applicationData.contactPersonEmails()

        assertThat(result)
            .containsExactlyInAnyOrder(
                hakijaContact.email,
                suorittajaContact.email,
                asianhoitajaContact.email,
                rakennuttajaContact.email,
            )
    }

    @Test
    fun `contactPersonEmails when not all types present should return existing`() {
        val applicationData =
            cableReport(
                customer = AlluDataFactory.hakijaCustomerContact,
                contractor = AlluDataFactory.suorittajaCustomerContact,
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result).containsExactlyInAnyOrder(hakijaContact.email, suorittajaContact.email)
    }

    @Test
    fun `contactPersonEmails when duplicate emails does not provide duplicates as output`() {
        val applicationData =
            cableReport(
                customer = AlluDataFactory.hakijaCustomerContact.plusContact(hakijaContact.email),
                contractor =
                    AlluDataFactory.suorittajaCustomerContact.plusContact(suorittajaContact.email),
                representative = AlluDataFactory.hakijaCustomerContact,
                developer = AlluDataFactory.suorittajaCustomerContact,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result).containsExactlyInAnyOrder(hakijaContact.email, suorittajaContact.email)
    }

    @Test
    fun `contactPersonEmails when omit present filters out given contact`() {
        val applicationData =
            cableReport(
                customer = AlluDataFactory.hakijaCustomerContact,
                contractor = AlluDataFactory.suorittajaCustomerContact,
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails(omit = suorittajaContact.email)

        assertThat(result).containsExactlyInAnyOrder(hakijaContact.email)
    }

    @Test
    fun `contactPersonEmails when omit is null does no filtering`() {
        val applicationData = cableReport()

        val result = applicationData.contactPersonEmails(omit = null)

        assertThat(result)
            .containsExactlyInAnyOrder(
                hakijaContact.email,
                suorittajaContact.email,
                asianhoitajaContact.email,
                rakennuttajaContact.email,
            )
    }

    @ParameterizedTest(name = "{index} => email=({0})")
    @ValueSource(strings = [" "])
    @NullAndEmptySource
    fun `contactPersonEmails when null, empty or blank email shouldn't be returned`(
        email: String?
    ) {
        val applicationData =
            cableReport(
                customer = AlluDataFactory.hakijaCustomerContact.modifyContactEmail(email),
                contractor = AlluDataFactory.suorittajaCustomerContact.modifyContactEmail(email),
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result).isEmpty()
    }

    private fun cableReport(
        customer: CustomerWithContacts = AlluDataFactory.hakijaCustomerContact,
        contractor: CustomerWithContacts = AlluDataFactory.suorittajaCustomerContact,
        representative: CustomerWithContacts? = AlluDataFactory.asianHoitajaCustomerContact,
        developer: CustomerWithContacts? = AlluDataFactory.rakennuttajaCustomerContact,
    ) =
        AlluDataFactory.createCableReportApplicationData(
            customerWithContacts = customer,
            contractorWithContacts = contractor,
            representativeWithContacts = representative,
            propertyDeveloperWithContacts = developer,
        )

    private fun CustomerWithContacts.modifyContactEmail(email: String?) =
        copy(contacts = contacts.map { it.copy(email = email) })

    private fun CustomerWithContacts.plusContact(email: String = AlluDataFactory.teppoEmail) =
        copy(contacts = contacts + AlluDataFactory.createContact(email = email))
}
