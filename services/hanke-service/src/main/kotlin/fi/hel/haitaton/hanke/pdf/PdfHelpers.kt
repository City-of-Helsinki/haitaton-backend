package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.getResource
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.io.ByteArrayOutputStream
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.print.PrintTranscoder
import org.openpdf.text.Chunk
import org.openpdf.text.Document
import org.openpdf.text.Element
import org.openpdf.text.Image
import org.openpdf.text.ImgTemplate
import org.openpdf.text.PageSize
import org.openpdf.text.Paragraph
import org.openpdf.text.Phrase
import org.openpdf.text.Rectangle
import org.openpdf.text.pdf.PdfContentByte
import org.openpdf.text.pdf.PdfPTable
import org.openpdf.text.pdf.PdfTemplate
import org.openpdf.text.pdf.PdfWriter
import org.openpdf.text.pdf.draw.VerticalPositionMark

/**
 * A4 paper size in figma is 1190 x 1684 px.
 *
 * In PDF, it's 595 x 842 pt.
 *
 * So when converting px to pt, we need to multiply by 595/1190 = 0.5 = 842/1684.
 */
const val PX_TO_PT = 0.5f

fun pxToPt(px: Int): Float = px * PX_TO_PT

fun Document.headerRow(leftSide: String, rightSide: String) {
    val glue = Chunk(VerticalPositionMark())
    val paragraph = Paragraph()
    paragraph.font = headerFont
    paragraph.add(leftSide)
    paragraph.add(glue)
    paragraph.add(rightSide)
    this.add(paragraph)
}

fun Document.title(title: String) {
    val paragraph = Paragraph(title, titleFont)
    paragraph.alignment = Element.ALIGN_LEFT
    paragraph.spacingAfter = pxToPt(16)
    this.add(paragraph)
}

fun Document.subtitle(title: String) {
    val paragraph = Paragraph(title, subtitleFont)
    paragraph.alignment = Element.ALIGN_LEFT
    paragraph.spacingBefore = -3.5f
    paragraph.spacingAfter = pxToPt(16)
    this.add(paragraph)
}

fun Document.mapHeader(header: String, locationIcon: ImgTemplate) {
    val table = PdfPTable(2)

    table.widthPercentage = 100f
    table.setTotalWidth(floatArrayOf(pxToPt(24), pxToPt(1078)))
    table.defaultCell.border = Rectangle.NO_BORDER
    table.defaultCell.isUseBorderPadding = true
    table.defaultCell.paddingTop = pxToPt(0)
    table.defaultCell.paddingBottom = pxToPt(0)
    table.defaultCell.paddingLeft = pxToPt(0)
    table.defaultCell.paddingRight = pxToPt(0)
    table.defaultCell.verticalAlignment = Element.ALIGN_CENTER

    val nextCell = table.addCell(locationIcon)
    nextCell.paddingLeft = pxToPt(8)
    table.addCell(Phrase(header, headerFont))

    table.setSpacingBefore(10.5f)
    table.setSpacingAfter(.4f)
    this.add(table)
}

fun PdfPTable.row(key: String, value: String?) {
    this.addCell(Phrase("$key ", rowHeaderFont))
    this.addCell(Phrase(value ?: "<Tyhjä>", textFont))
}

fun PdfPTable.row(value: Phrase) {
    this.addCell(defaultCell)
    this.addCell(value)
}

fun PdfPTable.row(key: String, image: Image) {
    this.addCell(Phrase("$key ", rowHeaderFont))
    this.addCell(image)
}

fun PdfPTable.emptyRow() {
    this.addCell("")
    this.addCell("")
}

fun PdfPTable.rowIfNotBlank(title: String, content: String?) {
    if (!content.isNullOrBlank()) {
        row(title, content)
    }
}

fun Document.section(sectionTitle: String?, addRows: PdfPTable.() -> Unit) {
    val paragraph = Paragraph()
    paragraph.keepTogether = true
    paragraph.spacingBefore = pxToPt(-39)

    if (!sectionTitle.isNullOrBlank()) {
        paragraph.add(Paragraph(sectionTitle, sectionTitleFont))
    }

    val table = PdfPTable(2)
    table.widthPercentage = 100f
    table.setTotalWidth(floatArrayOf(pxToPt(234), pxToPt(876)))
    table.defaultCell.border = Rectangle.NO_BORDER
    table.defaultCell.paddingTop = pxToPt(0)
    table.defaultCell.paddingBottom = pxToPt(16)
    table.defaultCell.paddingLeft = pxToPt(0)
    table.defaultCell.paddingRight = pxToPt(0)
    // Set line spacing to 24px
    table.defaultCell.setLeading(pxToPt(24), 0f)
    table.setSpacingBefore(-12f)
    table.isSplitRows = false
    table.isSplitLate = true

    table.addRows()
    paragraph.add(table)
    this.add(paragraph)
}

fun createDocument(addContent: (Document, PdfWriter) -> Unit): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val padding = pxToPt(40)
    val document = Document(PageSize.A4, padding, padding, padding, padding)
    val writer = PdfWriter.getInstance(document, outputStream)
    document.open()
    addContent(document, writer)
    document.close()
    return outputStream.toByteArray()
}

fun loadLocationIcon(writer: PdfWriter): ImgTemplate {
    val width = pxToPt(24)
    val height = pxToPt(24)
    val cb: PdfContentByte = writer.getDirectContent()
    val template: PdfTemplate = cb.createTemplate(width, height)
    val g2: Graphics2D = template.createGraphics(width, height)

    val prm = PrintTranscoder()
    val ti = TranscoderInput("/pdf-assets/location.svg".getResource().openStream())
    prm.transcode(ti, null)

    val pg = PageFormat()
    val pp = Paper()
    pp.setSize(width.toDouble(), height.toDouble())
    pp.setImageableArea(0.0, 0.0, width.toDouble(), height.toDouble())
    pg.paper = pp
    prm.print(g2, pg, 0)
    g2.dispose()

    return ImgTemplate(template)
}

fun nuisanceScore(
    index: Number?,
    title: String = "Työalueen haittaindeksi",
    color: NuisanceColor? = null,
): Paragraph {
    val p = Paragraph(title, blackNuisanceFont)
    val horizontalSpacer = Chunk("     ", blackNuisanceFont)
    p.add(horizontalSpacer)
    p.add(indexChunk(index?.toFloat(), color))
    return p
}

fun indexChunk(index: Float?, color: NuisanceColor? = null): Chunk {
    val formatted =
        if (index == null) "-"
        else if (index % 1.0 != 0.0) String.format("%s", index) else String.format("%.0f", index)

    val nuisanceColor = color ?: NuisanceColor.selectColor(index)

    val chunk = Chunk(formatted, nuisanceColor.font)
    chunk.setBackground(nuisanceColor.color, pxToPt(15), pxToPt(5), pxToPt(15), pxToPt(5))
    chunk.horizontalScaling
    return chunk
}
