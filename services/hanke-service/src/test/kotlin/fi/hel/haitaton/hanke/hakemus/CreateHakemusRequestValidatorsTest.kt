package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory.johtoselvitysRequest
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory.kaivuilmoitusRequest
import fi.hel.haitaton.hanke.hakemus.CreateHakemusRequestValidators.validateForErrors
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class CreateHakemusRequestValidatorsTest {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ValidateForErrors {
        @ParameterizedTest
        @MethodSource("fullCases")
        fun `succeeds with full information`(request: CreateHakemusRequest) {
            val result = request.validateForErrors()

            assertThat(result.isOk()).isTrue()
        }

        private fun fullCases(): List<CreateHakemusRequest> =
            listOf(johtoselvitysRequest(), kaivuilmoitusRequest())

        @ParameterizedTest
        @MethodSource("blankCases")
        fun `fails when the request has blank fields`(request: CreateHakemusRequest, path: String) {
            val result = request.validateForErrors()

            assertThat(result.isOk()).isFalse()
            assertThat(result.errorPaths()).single().isEqualTo(path)
        }

        private fun blankCases(): List<Arguments> =
            listOf(
                Arguments.of(johtoselvitysRequest(name = " "), "name"),
                Arguments.of(johtoselvitysRequest(workDescription = " "), "workDescription"),
                Arguments.of(
                    johtoselvitysRequest(postalAddress = " "),
                    "postalAddress.streetAddress.streetName"
                ),
                Arguments.of(kaivuilmoitusRequest(name = " "), "name"),
                Arguments.of(kaivuilmoitusRequest(workDescription = " "), "workDescription"),
            )

        @ParameterizedTest
        @MethodSource("emptyCases")
        fun `succeeds when the request has empty fields`(request: CreateHakemusRequest) {
            val result = request.validateForErrors()

            assertThat(result.isOk()).isTrue()
        }

        private fun emptyCases(): List<CreateHakemusRequest> =
            listOf(
                johtoselvitysRequest(name = ""),
                johtoselvitysRequest(workDescription = ""),
                johtoselvitysRequest(postalAddress = ""),
                kaivuilmoitusRequest(name = ""),
                kaivuilmoitusRequest(workDescription = ""),
            )

        @Test
        fun `fails when cableReportDone is false and rockExcavation is null`() {
            val request = kaivuilmoitusRequest(cableReportDone = false, rockExcavation = null)

            val result = request.validateForErrors()

            assertThat(result.isOk()).isFalse()
            assertThat(result.errorPaths()).single().isEqualTo("rockExcavation")
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        @NullSource
        fun `succeeds when cableReportDone is true`(rockExcavation: Boolean?) {
            val request =
                kaivuilmoitusRequest(cableReportDone = true, rockExcavation = rockExcavation)

            val result = request.validateForErrors()

            assertThat(result.isOk()).isTrue()
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `succeeds when cableReportDone is false and rockExcavation has value`(
            rockExcavation: Boolean?
        ) {
            val request =
                kaivuilmoitusRequest(cableReportDone = false, rockExcavation = rockExcavation)

            val result = request.validateForErrors()

            assertThat(result.isOk()).isTrue()
        }

        @Test
        fun `returns all failing paths when there are several`() {
            val request =
                johtoselvitysRequest(name = " ", workDescription = " ", postalAddress = " ")

            val result = request.validateForErrors()

            assertThat(result.isOk()).isFalse()
            assertThat(result.errorPaths())
                .containsExactlyInAnyOrder(
                    "name",
                    "workDescription",
                    "postalAddress.streetAddress.streetName"
                )
        }
    }
}
