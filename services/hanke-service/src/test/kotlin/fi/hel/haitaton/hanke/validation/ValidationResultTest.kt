package fi.hel.haitaton.hanke.validation

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ValidationResultTest {

    @Nested
    inner class HasOnlyHaittojenhallintasuunnitelmaErrors {

        @Test
        fun `returns false when there are no errors`() {
            val result = ValidationResult.success()

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isFalse()
        }

        @Test
        fun `returns true when all errors are about haittojenhallintasuunnitelma`() {
            val result =
                ValidationResult.failure("alueet[3].haittojenhallintasuunnitelma.AUTOLIIKENNE")
                    .and {
                        ValidationResult.failure(
                            "alueet[3].haittojenhallintasuunnitelma.PYORALIIKENNE"
                        )
                    }
                    .and { ValidationResult.failure("alueet[3].haittojenhallintasuunnitelma.MUUT") }
                    .and {
                        ValidationResult.failure("alueet[3].haittojenhallintasuunnitelma.YLEINEN")
                    }

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isTrue()
        }

        @Test
        fun `returns true when all errors are about haittojenhallintasuunnitelma from different areas`() {
            val result =
                ValidationResult.failure("alueet[0].haittojenhallintasuunnitelma.AUTOLIIKENNE")
                    .and {
                        ValidationResult.failure(
                            "alueet[1].haittojenhallintasuunnitelma.PYORALIIKENNE"
                        )
                    }
                    .and { ValidationResult.failure("alueet[2].haittojenhallintasuunnitelma.MUUT") }

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isTrue()
        }

        @Test
        fun `returns false when there are mixed error types - haittojenhallintasuunnitelma and other hanke fields`() {
            val result =
                ValidationResult.failure("alueet[3].haittojenhallintasuunnitelma.AUTOLIIKENNE")
                    .and { ValidationResult.failure("tyomaaKatuosoite") }
                    .and { ValidationResult.failure("nimi") }

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isFalse()
        }

        @Test
        fun `returns false when there are mixed error types - haittojenhallintasuunnitelma and other hankealue fields`() {
            val result =
                ValidationResult.failure("alueet[3].haittojenhallintasuunnitelma.AUTOLIIKENNE")
                    .and { ValidationResult.failure("alueet[3].nimi") }
                    .and { ValidationResult.failure("alueet[3].haittaAlkuPvm") }

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isFalse()
        }

        @Test
        fun `returns false when there are no haittojenhallintasuunnitelma errors`() {
            val result =
                ValidationResult.failure("tyomaaKatuosoite")
                    .and { ValidationResult.failure("nimi") }
                    .and { ValidationResult.failure("kuvaus") }

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isFalse()
        }

        @Test
        fun `returns false when only other hankealue fields have errors`() {
            val result =
                ValidationResult.failure("alueet[0].nimi")
                    .and { ValidationResult.failure("alueet[0].haittaAlkuPvm") }
                    .and { ValidationResult.failure("alueet[1].geometriat") }

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isFalse()
        }

        @Test
        fun `returns true when there is only one haittojenhallintasuunnitelma error`() {
            val result = ValidationResult.failure("alueet[0].haittojenhallintasuunnitelma.YLEINEN")

            val hasOnlyHaittaErrors = result.hasOnlyHaittojenhallintasuunnitelmaErrors()

            assertThat(hasOnlyHaittaErrors).isTrue()
        }
    }
}
