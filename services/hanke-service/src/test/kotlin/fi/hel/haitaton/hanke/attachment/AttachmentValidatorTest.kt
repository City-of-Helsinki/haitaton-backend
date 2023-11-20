package fi.hel.haitaton.hanke.attachment

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentValidator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class AttachmentValidatorTest {

    @Nested
    inner class ValidFilename {
        @Test
        fun `Normal filename should be valid`() {
            val filename = "example.txt"

            val result = AttachmentValidator.validFilename(filename)

            assertThat(result).isEqualTo(filename)
        }

        @Test
        fun `Filename in upper case should be valid`() {
            val filename = "EXAMPLE.TXT"

            val result = AttachmentValidator.validFilename(filename)

            assertThat(result).isEqualTo(filename)
        }

        @Test
        fun `Missing filename should cause exception`() {
            assertFailure { AttachmentValidator.validFilename("") }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: File '' not supported")
                }
        }

        @Test
        fun `Too long filename should cause an exception`() {
            val filename = "a".repeat(129)

            assertFailure { AttachmentValidator.validFilename(filename) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: File '$filename' not supported")
                }
        }

        @ParameterizedTest
        @CsvSource(
            "exa\\mple,exa_mple",
            "exa/mple,exa_mple",
            "example:,example_",
            "exa*mple,exa_mple",
            "examp?le,examp_le",
            "e\"xample,e_xample",
            "exa<mple,exa_mple",
            ">example,_example",
            "example|,example_",
            "exa%22mple,exa_mple",
            "exa*|%22:<>mple,exa______mple",
            "exa-mple,exa-mple",
        )
        fun `Invalid characters should be sanitized`(given: String, expected: String) {
            val filename = "$given.txt"

            val result = AttachmentValidator.validFilename(filename)

            assertThat(result).isEqualTo("$expected.txt")
        }

        @Test
        fun `Reserved filename should cause an exception`() {
            val filename = "con.txt"

            assertFailure { AttachmentValidator.validFilename(filename) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: File '$filename' not supported")
                }
        }

        @Test
        fun `PathTraversal should cause an exception`() {
            val filename = "../example.txt"

            assertFailure { AttachmentValidator.validFilename(filename) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: File '.._example.txt' not supported")
                }
        }

        @Test
        fun `Unsupported type should cause an exception`() {
            val filename = "file.gif"

            assertFailure { AttachmentValidator.validFilename(filename) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: File '$filename' not supported")
                }
        }
    }

    @Nested
    inner class EnsureMediaType {
        @ParameterizedTest
        @ValueSource(strings = ["invalid", ""])
        fun `Invalid media type should cause an exception`(input: String) {
            assertFailure { AttachmentValidator.ensureMediaType(input) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: Invalid content type, $input")
                }
        }

        @Test
        fun `Null content type should cause an exception`() {
            assertFailure { AttachmentValidator.ensureMediaType(null) }
                .all {
                    hasClass(AttachmentInvalidException::class)
                    hasMessage("Attachment upload exception: Content-Type null")
                }
        }
    }
}
