package fi.hel.haitaton.hanke.validation

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomer
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequestValidator
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.PostalAddressRequest
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test

class HakemusUpdateRequestValidatorTest {
    private val validator = HakemusUpdateRequestValidator()

    @Test
    fun `valid request has no errors`() {
        val request = HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()

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
                .copy(startTime = ZonedDateTime.now(), endTime = ZonedDateTime.now().minusDays(1))

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
                .copy(postalAddress = PostalAddressRequest(streetAddress = StreetAddress("   ")))

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
                "customerWithContacts.customer.phone"
            )
        }
    }

    @Test
    fun `customer registry key must be valid`() {
        val request =
            HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                .withCustomer(registryKey = "1234567-8")

        val exception = assertFailure { validator.isValid(request, null) }

        exception.all {
            hasClass(InvalidHakemusDataException::class)
            hasErrorPaths("customerWithContacts.customer.registryKey")
        }
    }

    private fun Assert<Throwable>.hasErrorPaths(vararg paths: String) {
        this.transform { it as InvalidHakemusDataException }
            .transform { it.errorPaths }
            .containsExactlyInAnyOrder(*paths)
    }
}
