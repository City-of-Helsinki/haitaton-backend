package fi.hel.haitaton.hanke.pdf

import assertk.Assert
import assertk.assertions.containsMatch
import fi.hel.haitaton.hanke.allu.Attachment
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import io.mockk.MockKVerificationScope
import org.openpdf.text.pdf.PdfReader
import org.openpdf.text.pdf.parser.PdfTextExtractor

fun MockKVerificationScope.withName(filename: String) =
    match<Attachment> { it.metadata.name == filename }

fun MockKVerificationScope.withNames(vararg filename: String) =
    match<List<ApplicationAttachmentMetadata>> {
        it.map { metadata -> metadata.fileName } == filename.toList()
    }

fun getPdfAsText(pdfData: ByteArray): String {
    val reader = PdfReader(pdfData)
    val pages = reader.numberOfPages
    val textExtractor = PdfTextExtractor(reader)
    return (1..pages).joinToString("\n") { textExtractor.getTextFromPage(it) }
}

fun Assert<String>.hasPhrase(phrase: String) = containsMatch(phraseAsRegexWithEscapes(phrase))

private fun phraseAsRegexWithEscapes(phrase: String): Regex =
    phrase.splitToSequence(' ').map { "\\Q$it\\E" }.joinToString("\\s+").toRegex()
