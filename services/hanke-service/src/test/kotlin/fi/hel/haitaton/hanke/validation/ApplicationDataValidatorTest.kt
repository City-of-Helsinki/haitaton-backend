package fi.hel.haitaton.hanke.validation

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.ExcavationNotificationData
import fi.hel.haitaton.hanke.application.validation.validateForErrors
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createApplication
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createCableReportApplicationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createCompanyCustomer
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createContact
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createExcavationNotificationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withCableReportApplicationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withExcavationNotificationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withInvoicingCustomer
import fi.hel.haitaton.hanke.touch
import fi.hel.haitaton.hanke.validation.ApplicationDataValidator.ensureValidForSend
import java.util.stream.Stream
import org.junit.jupiter.api.Nested
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

    @Nested
    inner class CableReport {
        @Test
        fun `Correct application passes validation`() {
            val applicationData = createCableReportApplicationData()

            assertThat(ensureValidForSend(applicationData)).isTrue()
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCableReportApplicationDataInput"
        )
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
                    .withCableReportApplicationData(
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
                    .withCableReportApplicationData(
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
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `CustomerWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication()
                    .withCableReportApplicationData(customerWithContacts = customerWithContacts)
            val applicationData = application.applicationData as CableReportApplicationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `ContractorWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication()
                    .withCableReportApplicationData(contractorWithContacts = customerWithContacts)
            val applicationData = application.applicationData as CableReportApplicationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `RepresentativeWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication()
                    .withCableReportApplicationData(
                        representativeWithContacts = customerWithContacts
                    )
            val applicationData = application.applicationData as CableReportApplicationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `RepresentativeWithContacts can be null`() {
            val application =
                createApplication()
                    .withCableReportApplicationData(representativeWithContacts = null)
                    .applicationData

            assertThat(ensureValidForSend(application)).isTrue()
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `PropertyDeveloperWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication()
                    .withCableReportApplicationData(
                        propertyDeveloperWithContacts = customerWithContacts
                    )
            val applicationData = application.applicationData as CableReportApplicationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `PropertyDeveloperWithContacts can be null`() {
            val application =
                createApplication()
                    .withCableReportApplicationData(propertyDeveloperWithContacts = null)

            assertThat(applicationValidator.isValid(application, null)).isTrue()
        }
    }

    @Nested
    inner class ExcavationNotification {
        @Test
        fun `Correct application passes validation`() {
            val applicationData = createExcavationNotificationData()

            assertThat(ensureValidForSend(applicationData)).isTrue()
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingExcavationNotificationDataInput"
        )
        fun `Missing fields ok as draft but not as non-draft`(
            missingField: String,
            applicationData: ExcavationNotificationData
        ) {
            missingField.touch()
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `No orderers ok as draft but not as final`() {
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(
                        customerWithContacts = customerWithNoOrderers,
                        contractorWithContacts = customerWithNoOrderers,
                    )
            val applicationData = application.applicationData as ExcavationNotificationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `No contacts ok as draft but not as final`() {
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(
                        customerWithContacts = customerWithNoContacts,
                        contractorWithContacts = customerWithNoContacts,
                    )
            val applicationData = application.applicationData as ExcavationNotificationData
            val contacts = applicationData.customersWithContacts().flatMap { it.contacts }
            assertThat(contacts).isEmpty()
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `CustomerWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(customerWithContacts = customerWithContacts)
            val applicationData = application.applicationData as ExcavationNotificationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `ContractorWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(contractorWithContacts = customerWithContacts)
            val applicationData = application.applicationData as ExcavationNotificationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `RepresentativeWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(
                        representativeWithContacts = customerWithContacts
                    )
            val applicationData = application.applicationData as ExcavationNotificationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `RepresentativeWithContacts can be null`() {
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(representativeWithContacts = null)
                    .applicationData

            assertThat(ensureValidForSend(application)).isTrue()
        }

        @ParameterizedTest(name = "{displayName}: missingField: {0}")
        @MethodSource(
            "fi.hel.haitaton.hanke.validation.ApplicationDataValidatorTestKt#missingCustomerWithContactsInput"
        )
        fun `PropertyDeveloperWithContacts missing fields ok as draft but not as non-draft`(
            missingField: String,
            customerWithContacts: CustomerWithContacts
        ) {
            missingField.touch()
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(
                        propertyDeveloperWithContacts = customerWithContacts
                    )
            val applicationData = application.applicationData as ExcavationNotificationData
            assertThat(applicationData.validateForErrors().isOk()).isTrue()

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }

        @Test
        fun `PropertyDeveloperWithContacts can be null`() {
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withExcavationNotificationData(propertyDeveloperWithContacts = null)
            val applicationData = application.applicationData as ExcavationNotificationData

            assertThat(ensureValidForSend(applicationData)).isTrue()
        }

        @Test
        fun `Valid OVT is allowed`() {
            val businessId = "2182805-0"
            val validOVT = "003721828050"
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withInvoicingCustomer(
                        ApplicationFactory.createCompanyInvoicingCustomer(
                            registryKey = businessId,
                            ovt = validOVT
                        )
                    )
            val applicationData = application.applicationData as ExcavationNotificationData

            assertThat(ensureValidForSend(applicationData)).isTrue()
        }

        @Test
        fun `Invalid OVT produces failure`() {
            val businessId = "2182805-0"
            val invalidOVT = "003721828053"
            val application =
                createApplication(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                    .withInvoicingCustomer(
                        ApplicationFactory.createCompanyInvoicingCustomer(
                            registryKey = businessId,
                            ovt = invalidOVT
                        )
                    )
            val applicationData = application.applicationData as ExcavationNotificationData

            assertFailure { ensureValidForSend(applicationData) }
                .hasClass(InvalidApplicationDataException::class)
        }
    }
}

fun missingCableReportApplicationDataInput(): Stream<Arguments> =
    Stream.of(
        of("Empty name", createCableReportApplicationData(name = "")),
        of("Empty work description", createCableReportApplicationData(workDescription = "")),
        of("Null start time", createCableReportApplicationData(startTime = null)),
        of("Null end time", createCableReportApplicationData(endTime = null)),
        of("Null areas", createCableReportApplicationData(areas = null)),
        of("Null rock excavation", createCableReportApplicationData().copy(rockExcavation = null)),
        of("Null customer", createCableReportApplicationData().copy(customerWithContacts = null)),
        of(
            "Null contractor",
            createCableReportApplicationData().copy(contractorWithContacts = null)
        ),
    )

fun missingExcavationNotificationDataInput(): Stream<Arguments> =
    Stream.of(
        of("Empty name", createExcavationNotificationData(name = "")),
        of("Empty work description", createExcavationNotificationData(workDescription = "")),
        of("No required competence", createExcavationNotificationData(requiredCompetence = false)),
        of("Null start time", createExcavationNotificationData(startTime = null)),
        of("Null end time", createExcavationNotificationData(endTime = null)),
        of("Null areas", createExcavationNotificationData(areas = null)),
        of("Null rock excavation", createExcavationNotificationData().copy(rockExcavation = null)),
        of("Null customer", createExcavationNotificationData(customerWithContacts = null)),
        of("Null contractor", createExcavationNotificationData(contractorWithContacts = null)),
    )

fun missingCustomerWithContactsInput(): Stream<Arguments> =
    Stream.of(
        of("Customer null type", createCompanyCustomer(type = null).withContacts()),
        of("Customer empty name", createCompanyCustomer(name = "").withContacts()),
        of(
            "Contact empty name",
            createCompanyCustomer(name = "")
                .withContacts(createContact(firstName = "", lastName = ""))
        )
    )
