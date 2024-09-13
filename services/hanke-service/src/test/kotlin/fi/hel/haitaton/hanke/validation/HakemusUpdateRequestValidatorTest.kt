package fi.hel.haitaton.hanke.validation

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasClass
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomer
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withInvoicingCustomer
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsRequest
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequestValidator
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.InvoicingPostalAddressRequest
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
import fi.hel.haitaton.hanke.hakemus.StreetAddress
import fi.hel.haitaton.hanke.hakemus.validateRegistryKey
import fi.hel.haitaton.hanke.test.Asserts.succeeds
import java.time.ZonedDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

class HakemusUpdateRequestValidatorTest {
    private val validator = HakemusUpdateRequestValidator()

    private val validHetu = "110296-926B"
    private val validYtunnus = "7356217-8"

    @Nested
    inner class WithJohtoselvityshakemusUpdateRequest {
        @Test
        fun `valid request has no errors`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()

            val result = validator.isValid(request, null)

            assertThat(result).isTrue()
        }

        @Test
        fun `name cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .copy(name = "   ")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("name")
            }
        }

        @Test
        fun `work description cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .copy(workDescription = "   ")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("workDescription")
            }
        }

        @Test
        fun `end time cannot be before start time`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .copy(
                        startTime = ZonedDateTime.now(),
                        endTime = ZonedDateTime.now().minusDays(1),
                    )

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("endTime")
            }
        }

        @Test
        fun `street name cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .copy(
                        postalAddress = PostalAddressRequest(streetAddress = StreetAddress("   ")))

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("postalAddress.streetAddress.streetName")
            }
        }

        @Test
        fun `customer fields cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withCustomer(
                        name = "   ",
                        email = "   ",
                        phone = "   ",
                    )

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths(
                    "customerWithContacts.customer.name",
                    "customerWithContacts.customer.email",
                    "customerWithContacts.customer.phone",
                )
            }
        }

        @Test
        fun `customer registry key must be valid when customer is a company`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withCustomer(registryKey = "1234567-8")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("customerWithContacts.customer.registryKey")
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `customer registry key must be null when the customer is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "customerWithContacts") { request, customer ->
                request.copy(customerWithContacts = customer)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `contractor registry key must be null when the contractor is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "contractorWithContacts") { request, customer ->
                request.copy(contractorWithContacts = customer)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `representative registry key must be null when the representative is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "representativeWithContacts") { request, customer ->
                request.copy(representativeWithContacts = customer)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `developer registry key must be null when the developer is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "propertyDeveloperWithContacts") { request, customer ->
                request.copy(propertyDeveloperWithContacts = customer)
            }
        }

        private fun testRegistryKey(
            type: CustomerType,
            pathPrefix: String,
            addCustomer:
                (
                    JohtoselvityshakemusUpdateRequest,
                    CustomerWithContactsRequest) -> JohtoselvityshakemusUpdateRequest
        ) {
            val customer =
                HakemusUpdateRequestFactory.createCustomerWithContactsRequest(
                    customerType = type, registryKey = validHetu)
            val request =
                addCustomer(
                    HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest(),
                    customer)

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("$pathPrefix.customer.registryKey")
            }
        }
    }

    @Nested
    inner class WithKaivuilmoitusUpdateRequest {
        @Test
        fun `valid request has no errors`() {
            val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()

            val result = validator.isValid(request, null)

            assertThat(result).isTrue()
        }

        @Test
        fun `name cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .copy(name = "   ")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("name")
            }
        }

        @Test
        fun `work description cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .copy(workDescription = "   ")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("workDescription")
            }
        }

        @Test
        fun `end time cannot be before start time`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .copy(
                        startTime = ZonedDateTime.now(),
                        endTime = ZonedDateTime.now().minusDays(1),
                    )

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("endTime")
            }
        }

        @Test
        fun `customer fields cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withCustomer(
                        name = "   ",
                        email = "   ",
                        phone = "   ",
                    )

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths(
                    "customerWithContacts.customer.name",
                    "customerWithContacts.customer.email",
                    "customerWithContacts.customer.phone",
                )
            }
        }

        @Test
        fun `customer registry key must be valid`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withCustomer(registryKey = "1234567-8")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("customerWithContacts.customer.registryKey")
            }
        }

        @Test
        fun `invoicing customer fields cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withInvoicingCustomer(
                        name = "   ",
                        postalAddressRequest =
                            InvoicingPostalAddressRequest(
                                streetAddress = StreetAddress("   "),
                                postalCode = "   ",
                                city = "   ",
                            ),
                        email = "   ",
                        phone = "   ",
                        registryKey = "   ",
                        ovt = "   ",
                        invoicingOperator = "   ",
                    )

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths(
                    "invoicingCustomer.name",
                    "invoicingCustomer.postalAddress.streetAddress.streetName",
                    "invoicingCustomer.postalAddress.postalCode",
                    "invoicingCustomer.postalAddress.city",
                    "invoicingCustomer.email",
                    "invoicingCustomer.phone",
                    "invoicingCustomer.registryKey",
                    "invoicingCustomer.ovt",
                    "invoicingCustomer.invoicingOperator",
                )
            }
        }

        @Test
        fun `invoicing customer address is mandatory when OVT is not given`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withInvoicingCustomer(
                        postalAddressRequest = null,
                        ovt = null,
                    )

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths(
                    "invoicingCustomer.postalAddress",
                )
            }
        }

        @Test
        fun `invoicing customer registry key must be valid`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withInvoicingCustomer(registryKey = "1234567-8")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("invoicingCustomer.registryKey")
            }
        }

        @Test
        fun `invoicing customer OVT must be valid`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withInvoicingCustomer(ovt = "12345678901")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("invoicingCustomer.ovt")
            }
        }

        @Test
        fun `additional info cannot be just whitespaces`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .copy(additionalInfo = "   ")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("additionalInfo")
            }
        }

        @Test
        fun `customer registry key must be valid henkilotunnus when the customer is a person`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withCustomer(CustomerType.PERSON, registryKey = "false")

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("customerWithContacts.customer.registryKey")
            }
        }

        @Test
        fun `customer registry key can be anything when the customer is an other`() {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withCustomer(CustomerType.OTHER, registryKey = "false")

            val result = validator.isValid(request, null)

            assertThat(result).isTrue()
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `is valid when customer registry key is a valid henkilotunnus and customer is a person or an other`(
            type: CustomerType
        ) {
            val request =
                HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
                    .withCustomer(type, registryKey = validHetu)

            val result = validator.isValid(request, null)

            assertThat(result).isTrue()
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `contractor registry key must be null when the contractor is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "contractorWithContacts") { request, customer ->
                request.copy(contractorWithContacts = customer)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `representative registry key must be null when the representative is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "representativeWithContacts") { request, customer ->
                request.copy(representativeWithContacts = customer)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `developer registry key must be null when the developer is a person or an other`(
            type: CustomerType
        ) {
            testRegistryKey(type, "propertyDeveloperWithContacts") { request, customer ->
                request.copy(propertyDeveloperWithContacts = customer)
            }
        }

        private fun testRegistryKey(
            type: CustomerType,
            pathPrefix: String,
            addCustomer:
                (
                    KaivuilmoitusUpdateRequest,
                    CustomerWithContactsRequest) -> KaivuilmoitusUpdateRequest
        ) {
            val customer =
                HakemusUpdateRequestFactory.createCustomerWithContactsRequest(
                    customerType = type, registryKey = validHetu)
            val request =
                addCustomer(
                    HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest(), customer)

            val exception = assertFailure { validator.isValid(request, null) }

            exception.all {
                hasClass(InvalidHakemusDataException::class)
                hasErrorPaths("$pathPrefix.customer.registryKey")
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ValidateRegistryKey {

        private fun otherSuccessParams() =
            listOf(
                Arguments.of(CustomerType.OTHER, true, null),
                Arguments.of(CustomerType.OTHER, false, null),
                Arguments.of(CustomerType.OTHER, false, validHetu),
                Arguments.of(CustomerType.OTHER, false, "invalid"),
            )

        private fun otherFailureParams() =
            listOf(
                Arguments.of(CustomerType.OTHER, true, ""),
                Arguments.of(CustomerType.OTHER, true, validHetu),
                Arguments.of(CustomerType.OTHER, false, ""),
            )

        private fun personSuccessParams() =
            listOf(
                Arguments.of(CustomerType.PERSON, true, null),
                Arguments.of(CustomerType.PERSON, false, null),
                Arguments.of(CustomerType.PERSON, false, validHetu),
            )

        private fun personFailureParams() =
            listOf(
                Arguments.of(CustomerType.PERSON, true, ""),
                Arguments.of(CustomerType.PERSON, true, validHetu),
                Arguments.of(CustomerType.PERSON, false, ""),
                Arguments.of(CustomerType.PERSON, false, "invalid"),
            )

        private fun companySuccessParams() =
            listOf(
                Arguments.of(CustomerType.COMPANY, false, null),
                Arguments.of(CustomerType.COMPANY, false, validYtunnus),
            )

        private fun companyFailureParams() =
            listOf(
                Arguments.of(CustomerType.COMPANY, true, null),
                Arguments.of(CustomerType.COMPANY, true, ""),
                Arguments.of(CustomerType.COMPANY, true, validYtunnus),
                Arguments.of(CustomerType.COMPANY, false, ""),
                Arguments.of(CustomerType.COMPANY, false, "invalid"),
            )

        private fun associationSuccessParams() =
            companySuccessParams().map {
                it.get().let { args -> Arguments.of(CustomerType.ASSOCIATION, args[1], args[2]) }
            }

        private fun associationFailureParams() =
            companyFailureParams().map {
                it.get().let { args -> Arguments.of(CustomerType.ASSOCIATION, args[1], args[2]) }
            }

        @ParameterizedTest
        @MethodSource(
            value =
                [
                    "personSuccessParams",
                    "otherSuccessParams",
                    "companySuccessParams",
                    "associationSuccessParams",
                ])
        fun `succeeds when parameters are correct`(
            type: CustomerType,
            registryKeyHidden: Boolean,
            registryKey: String?,
        ) {
            val request =
                HakemusUpdateRequestFactory.createCustomer(
                    type = type, registryKey = registryKey, registryKeyHidden = registryKeyHidden)

            val result = request.validateRegistryKey("path")

            assertThat(result).succeeds()
        }

        @ParameterizedTest
        @MethodSource(
            value =
                [
                    "personFailureParams",
                    "otherFailureParams",
                    "companyFailureParams",
                    "associationFailureParams",
                ])
        fun `fails when parameters are incorrect`(
            type: CustomerType,
            registryKeyHidden: Boolean,
            registryKey: String?,
        ) {
            val request =
                HakemusUpdateRequestFactory.createCustomer(
                    type = type, registryKey = registryKey, registryKeyHidden = registryKeyHidden)

            val result = request.validateRegistryKey("path")

            assertThat(result).prop(ValidationResult::isOk).isFalse()
        }
    }

    private fun Assert<Throwable>.hasErrorPaths(vararg paths: String) {
        this.transform { it as InvalidHakemusDataException }
            .transform { it.errorPaths }
            .containsExactlyInAnyOrder(*paths)
    }
}
