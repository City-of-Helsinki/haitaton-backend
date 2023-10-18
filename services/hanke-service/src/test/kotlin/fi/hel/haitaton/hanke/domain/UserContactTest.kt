package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianHoitajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.asianhoitajaContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.hakijaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.rakennuttajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.suorittajaCustomerContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.teppoEmail
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class UserContactTest {

    @Test
    fun `UserContact when valid input returns contact`() {
        assertThat(UserContact.from(TEPPO_TESTI, teppoEmail))
            .isEqualTo(UserContact(TEPPO_TESTI, teppoEmail))
    }

    @ParameterizedTest
    @CsvSource("name,", ",email", "' ',", ",' '")
    fun `UserContact when invalid input returns null`(name: String?, email: String?) {
        assertThat(UserContact.from(name, email)).isNull()
    }

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
                customer = hakijaCustomerContact,
                contractor = suorittajaCustomerContact,
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
                customer = hakijaCustomerContact.plusContact(hakijaContact.email),
                contractor = suorittajaCustomerContact.plusContact(suorittajaContact.email),
                representative = hakijaCustomerContact,
                developer = suorittajaCustomerContact,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result).containsExactlyInAnyOrder(hakijaContact.email, suorittajaContact.email)
    }

    @Test
    fun `contactPersonEmails when omit present filters out given contact`() {
        val applicationData =
            cableReport(
                customer = hakijaCustomerContact,
                contractor = suorittajaCustomerContact,
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
                customer = hakijaCustomerContact.modifyContactEmail(email),
                contractor = suorittajaCustomerContact.modifyContactEmail(email),
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result).isEmpty()
    }
}

private fun cableReport(
    customer: CustomerWithContacts = hakijaCustomerContact,
    contractor: CustomerWithContacts = suorittajaCustomerContact,
    representative: CustomerWithContacts? = asianHoitajaCustomerContact,
    developer: CustomerWithContacts? = rakennuttajaCustomerContact,
) =
    AlluDataFactory.createCableReportApplicationData(
        customerWithContacts = customer,
        contractorWithContacts = contractor,
        representativeWithContacts = representative,
        propertyDeveloperWithContacts = developer,
    )

private fun CustomerWithContacts.modifyContactEmail(email: String?) =
    copy(contacts = contacts.map { it.copy(email = email) })

private fun CustomerWithContacts.plusContact(email: String = teppoEmail) =
    copy(contacts = contacts + createContact(email = email))
