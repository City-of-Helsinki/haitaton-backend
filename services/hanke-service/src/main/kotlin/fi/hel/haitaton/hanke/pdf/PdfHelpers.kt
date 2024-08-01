package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPTable
import java.time.format.DateTimeFormatter

val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
val sectionFont = Font(Font.HELVETICA, 15f, Font.BOLD)

val headerFont = Font(Font.HELVETICA, 10f, Font.BOLD)
val textFont = Font(Font.HELVETICA, 10f, Font.NORMAL)

val finnishDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.uuuu")

fun Document.newline() {
    this.add(Paragraph(Chunk.NEWLINE))
}

fun Document.title(title: String) {
    val paragraph = Paragraph(title, titleFont)
    paragraph.alignment = Element.ALIGN_CENTER
    this.add(paragraph)
    this.newline()
}

fun Document.sectionTitle(sectionTitle: String) {
    val paragraph = Paragraph(sectionTitle, sectionFont)
    this.add(paragraph)
    this.newline()
}

fun PdfPTable.row(key: String, value: Any?) {
    this.addCell(Phrase("$key ", headerFont))
    this.addCell(Phrase(value?.toString() ?: "<Tyhjä>", textFont))
}

fun PdfPTable.rowIfNotBlank(title: String, content: String?) {
    if (!content.isNullOrBlank()) {
        row(title, content)
    }
}

fun Document.section(sectionTitle: String, addRows: PdfPTable.() -> Unit) {
    this.sectionTitle(sectionTitle)

    val table = PdfPTable(2)
    table.widthPercentage = 100f
    table.setWidths(floatArrayOf(1.5f, 3.5f))
    table.setSpacingBefore(10f)
    table.defaultCell.border = Rectangle.NO_BORDER
    table.defaultCell.paddingBottom = 15f

    table.addRows()

    this.add(table)
}
