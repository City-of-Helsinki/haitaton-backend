package fi.hel.haitaton.hanke.attachment.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class BlobMetadataFixerTest {

    @Nested
    inner class NeedsFixing {

        @Test
        fun `returns false for properly encoded RFC 5987 format`() {
            val disposition = "attachment; filename*=UTF-8''test%2Cfile.pdf"

            assertThat(BlobMetadataFixer.needsFixing(disposition)).isFalse()
        }

        @Test
        fun `returns true for RFC 5987 format with unencoded special characters`() {
            val dispositions =
                listOf(
                    "attachment; filename*=UTF-8''test,file.pdf", // Unencoded comma
                    "attachment; filename*=UTF-8''test;file.pdf", // Unencoded semicolon
                    "attachment; filename*=UTF-8''test*file.pdf", // Unencoded asterisk
                    "attachment; filename*=UTF-8''test\"file.pdf", // Unencoded quote
                    "attachment; filename*=UTF-8''test'file.pdf", // Unencoded single quote
                )

            dispositions.forEach { disposition ->
                assertThat(BlobMetadataFixer.needsFixing(disposition)).isTrue()
            }
        }

        @ParameterizedTest
        @CsvSource(
            "attachment; filename=\"test.pdf\"",
            "attachment; filename=test.pdf",
            "inline; filename=\"document.txt\"",
        )
        fun `returns true for non-RFC 5987 format`(disposition: String) {
            assertThat(BlobMetadataFixer.needsFixing(disposition)).isTrue()
        }
    }

    @Nested
    inner class ExtractOriginalFilename {

        @Test
        fun `extracts and decodes filename from RFC 5987 format`() {
            val testCases =
                mapOf(
                    "attachment; filename*=UTF-8''test.pdf" to "test.pdf",
                    "attachment; filename*=UTF-8''test%2Cfile.pdf" to "test,file.pdf",
                    "attachment; filename*=UTF-8''test%20file.pdf" to "test file.pdf",
                    "attachment; filename*=UTF-8''%C3%A5%C3%A4%C3%B6.pdf" to "åäö.pdf",
                )

            testCases.forEach { (disposition, expectedFilename) ->
                assertThat(BlobMetadataFixer.extractOriginalFilename(disposition))
                    .isEqualTo(expectedFilename)
            }
        }

        @ParameterizedTest
        @CsvSource(
            "'attachment; filename=\"test.pdf\"','test.pdf'",
            "'attachment; filename=test.pdf','test.pdf'",
            "'inline; filename=\"document.txt\"','document.txt'",
        )
        fun `extracts filename from simple format`(disposition: String, expectedFilename: String) {
            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition))
                .isEqualTo(expectedFilename)
        }

        @Test
        fun `returns null for invalid disposition`() {
            assertThat(BlobMetadataFixer.extractOriginalFilename("attachment")).isNull()
        }

        @Test
        fun `returns null for disposition with only attachment type`() {
            assertThat(BlobMetadataFixer.extractOriginalFilename("inline")).isNull()
        }

        @Test
        fun `handles disposition with semicolon after filename`() {
            val disposition = "attachment; filename*=UTF-8''test.pdf; size=1234"

            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition)).isEqualTo("test.pdf")
        }

        @Test
        fun `handles disposition with multiple parameters after filename`() {
            val disposition =
                "attachment; filename*=UTF-8''document.pdf; size=1234; creation-date=\"Mon, 01 Jan 2024\""

            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition))
                .isEqualTo("document.pdf")
        }

        @Test
        fun `handles disposition with spaces around filename`() {
            val disposition = "attachment; filename*=UTF-8''  test.pdf  "

            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition)).isEqualTo("test.pdf")
        }

        @Test
        fun `handles filename with percent encoding for special characters`() {
            val testCases =
                mapOf(
                    "attachment; filename*=UTF-8''file%2A.pdf" to "file*.pdf", // asterisk
                    "attachment; filename*=UTF-8''file%3B.pdf" to "file;.pdf", // semicolon
                    "attachment; filename*=UTF-8''file%27.pdf" to "file'.pdf", // single quote
                    "attachment; filename*=UTF-8''file%22.pdf" to "file\".pdf", // double quote
                )

            testCases.forEach { (disposition, expectedFilename) ->
                assertThat(BlobMetadataFixer.extractOriginalFilename(disposition))
                    .isEqualTo(expectedFilename)
            }
        }

        @Test
        fun `handles filename with unquoted simple format`() {
            val disposition = "attachment; filename=simple-file.pdf"

            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition))
                .isEqualTo("simple-file.pdf")
        }

        @Test
        fun `handles filename with quoted simple format containing spaces`() {
            val disposition = "attachment; filename=\"file with spaces.pdf\""

            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition))
                .isEqualTo("file with spaces.pdf")
        }

        @Test
        fun `returns null for malformed RFC 5987 with invalid percent encoding`() {
            // Invalid percent encoding should return null due to decode exception
            val disposition = "attachment; filename*=UTF-8''file%ZZ.pdf"

            assertThat(BlobMetadataFixer.extractOriginalFilename(disposition)).isNull()
        }
    }
}
