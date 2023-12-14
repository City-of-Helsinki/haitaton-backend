package fi.hel.haitaton.hanke.application

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_RAKENNUTTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_SUORITTAJA
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
                KAYTTAJA_INPUT_HAKIJA.email,
                KAYTTAJA_INPUT_SUORITTAJA.email,
                KAYTTAJA_INPUT_ASIANHOITAJA.email,
                KAYTTAJA_INPUT_RAKENNUTTAJA.email,
            )
    }

    @Test
    fun `contactPersonEmails when not all types present should return existing`() {
        val applicationData =
            cableReport(
                customer = ApplicationFactory.hakijaCustomerContact,
                contractor = ApplicationFactory.suorittajaCustomerContact,
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result)
            .containsExactlyInAnyOrder(KAYTTAJA_INPUT_HAKIJA.email, KAYTTAJA_INPUT_SUORITTAJA.email)
    }

    @Test
    fun `contactPersonEmails when duplicate emails does not provide duplicates as output`() {
        val applicationData =
            cableReport(
                customer =
                    ApplicationFactory.hakijaCustomerContact.plusContact(
                        KAYTTAJA_INPUT_HAKIJA.email
                    ),
                contractor =
                    ApplicationFactory.suorittajaCustomerContact.plusContact(
                        KAYTTAJA_INPUT_SUORITTAJA.email
                    ),
                representative = ApplicationFactory.hakijaCustomerContact,
                developer = ApplicationFactory.suorittajaCustomerContact,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result)
            .containsExactlyInAnyOrder(KAYTTAJA_INPUT_HAKIJA.email, KAYTTAJA_INPUT_SUORITTAJA.email)
    }

    @Test
    fun `contactPersonEmails when omit present filters out given contact`() {
        val applicationData =
            cableReport(
                customer = ApplicationFactory.hakijaCustomerContact,
                contractor = ApplicationFactory.suorittajaCustomerContact,
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails(omit = KAYTTAJA_INPUT_SUORITTAJA.email)

        assertThat(result).containsExactlyInAnyOrder(KAYTTAJA_INPUT_HAKIJA.email)
    }

    @Test
    fun `contactPersonEmails when omit is null does no filtering`() {
        val applicationData = cableReport()

        val result = applicationData.contactPersonEmails(omit = null)

        assertThat(result)
            .containsExactlyInAnyOrder(
                KAYTTAJA_INPUT_HAKIJA.email,
                KAYTTAJA_INPUT_SUORITTAJA.email,
                KAYTTAJA_INPUT_ASIANHOITAJA.email,
                KAYTTAJA_INPUT_RAKENNUTTAJA.email,
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
                customer = ApplicationFactory.hakijaCustomerContact.modifyContactEmail(email),
                contractor = ApplicationFactory.suorittajaCustomerContact.modifyContactEmail(email),
                representative = null,
                developer = null,
            )

        val result = applicationData.contactPersonEmails()

        assertThat(result).isEmpty()
    }

    private fun cableReport(
        customer: CustomerWithContacts = ApplicationFactory.hakijaCustomerContact,
        contractor: CustomerWithContacts = ApplicationFactory.suorittajaCustomerContact,
        representative: CustomerWithContacts? = ApplicationFactory.asianHoitajaCustomerContact,
        developer: CustomerWithContacts? = ApplicationFactory.rakennuttajaCustomerContact,
    ) =
        ApplicationFactory.createCableReportApplicationData(
            customerWithContacts = customer,
            contractorWithContacts = contractor,
            representativeWithContacts = representative,
            propertyDeveloperWithContacts = developer,
        )

    private fun CustomerWithContacts.modifyContactEmail(email: String?) =
        copy(contacts = contacts.map { it.copy(email = email) })

    private fun CustomerWithContacts.plusContact(email: String = ApplicationFactory.TEPPO_EMAIL) =
        copy(contacts = contacts + ApplicationFactory.createContact(email = email))
}
