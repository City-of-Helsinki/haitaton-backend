package fi.hel.haitaton.hanke.validation

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.validation.validateForErrors
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createApplication
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createCableReportApplicationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createCompanyCustomer
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createContact
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withApplicationData
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.touch
import fi.hel.haitaton.hanke.validation.ApplicationDataValidator.ensureValidForSend
import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.of
import org.junit.jupiter.params.provider.MethodSource

class ApplicationDataValidatorTest {
    private val applicationValidator = ApplicationValidator()

    private val customerWithNoOrderers =
        createCompanyCustomer()
            .withContacts(
                createContact(orderer = false),
                createContact(orderer = false),
            )

    private val customerWithNoContacts = createCompanyCustomer().withContacts()

    @Test
    fun `Correct application passes validation`() {
        val applicationData = createCableReportApplicationData()

        assertThat(ensureValidForSend(applicationData)).isTrue()
    }

    @ParameterizedTest(name = "{displayName}: missingField: {0}")
    @MethodSource("missingDataInput")
    fun `Missing fields ok as draft but not as non-draft`(
        missingField: String,
        cableReportApplicationData: CableReportApplicationData
    ) {
        missingField.touch()
        assertThat(cableReportApplicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(cableReportApplicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @Test
    fun `No orderers ok as draft but not as final`() {
        val application =
            createApplication()
                .withApplicationData(
                    customerWithContacts = customerWithNoOrderers,
                    contractorWithContacts = customerWithNoOrderers,
                )
        val applicationData = application.applicationData as CableReportApplicationData
        assertThat(applicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(applicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @Test
    fun `No contacts ok as draft but not as final`() {
        val application =
            createApplication()
                .withApplicationData(
                    customerWithContacts = customerWithNoContacts,
                    contractorWithContacts = customerWithNoContacts,
                )
        val applicationData = application.applicationData as CableReportApplicationData
        val contacts = applicationData.customersWithContacts().flatMap { it.contacts }
        assertThat(contacts).isEmpty()
        assertThat(applicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(applicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @ParameterizedTest(name = "{displayName}: missingField: {0}")
    @MethodSource("missingCustomerWithContactsInput")
    fun `CustomerWithContacts missing fields ok as draft but not as non-draft`(
        missingField: String,
        customerWithContacts: CustomerWithContacts
    ) {
        missingField.touch()
        val application =
            createApplication().withApplicationData(customerWithContacts = customerWithContacts)
        val applicationData = application.applicationData as CableReportApplicationData
        assertThat(applicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(applicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @ParameterizedTest(name = "{displayName}: missingField: {0}")
    @MethodSource("missingCustomerWithContactsInput")
    fun `ContractorWithContacts missing fields ok as draft but not as non-draft`(
        missingField: String,
        customerWithContacts: CustomerWithContacts
    ) {
        missingField.touch()
        val application =
            createApplication().withApplicationData(contractorWithContacts = customerWithContacts)
        val applicationData = application.applicationData as CableReportApplicationData
        assertThat(applicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(applicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @ParameterizedTest(name = "{displayName}: missingField: {0}")
    @MethodSource("missingCustomerWithContactsInput")
    fun `RepresentativeWithContacts missing fields ok as draft but not as non-draft`(
        missingField: String,
        customerWithContacts: CustomerWithContacts
    ) {
        missingField.touch()
        val application =
            createApplication()
                .withApplicationData(representativeWithContacts = customerWithContacts)
        val applicationData = application.applicationData as CableReportApplicationData
        assertThat(applicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(applicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @Test
    fun `RepresentativeWithContacts can be null`() {
        val application =
            createApplication()
                .withApplicationData(representativeWithContacts = null)
                .applicationData

        assertThat(ensureValidForSend(application)).isTrue()
    }

    @ParameterizedTest(name = "{displayName}: missingField: {0}")
    @MethodSource("missingCustomerWithContactsInput")
    fun `PropertyDeveloperWithContacts missing fields ok as draft but not as non-draft`(
        missingField: String,
        customerWithContacts: CustomerWithContacts
    ) {
        missingField.touch()
        val application =
            createApplication()
                .withApplicationData(propertyDeveloperWithContacts = customerWithContacts)
        val applicationData = application.applicationData as CableReportApplicationData
        assertThat(applicationData.validateForErrors().isOk()).isTrue()

        assertFailure { ensureValidForSend(applicationData) }
            .hasClass(InvalidApplicationDataException::class)
    }

    @Test
    fun `PropertyDeveloperWithContacts can be null`() {
        val application =
            createApplication().withApplicationData(propertyDeveloperWithContacts = null)

        assertThat(applicationValidator.isValid(application, null)).isTrue()
    }

    companion object {
        @JvmStatic
        private fun missingDataInput(): Stream<Arguments> =
            Stream.of(
                of("Empty name", createCableReportApplicationData(name = "")),
                of(
                    "Empty work description",
                    createCableReportApplicationData(workDescription = "")
                ),
                of("Null start time", createCableReportApplicationData(startTime = null)),
                of("Null end time", createCableReportApplicationData(endTime = null)),
                of("Null areas", createCableReportApplicationData(areas = null)),
                of(
                    "Null rock excavation",
                    createCableReportApplicationData().copy(rockExcavation = null)
                )
            )

        @JvmStatic
        private fun missingCustomerWithContactsInput(): Stream<Arguments> =
            Stream.of(
                of("Customer null type", createCompanyCustomer(type = null).withContacts()),
                of("Customer empty name", createCompanyCustomer(name = "").withContacts()),
                of(
                    "Contact empty name",
                    createCompanyCustomer(name = "")
                        .withContacts(createContact(firstName = "", lastName = ""))
                )
            )
    }
}
