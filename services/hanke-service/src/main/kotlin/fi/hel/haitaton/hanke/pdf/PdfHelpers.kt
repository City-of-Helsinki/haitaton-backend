package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.ImgTemplate
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.BaseFont
import com.lowagie.text.pdf.PdfContentByte
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfTemplate
import com.lowagie.text.pdf.PdfWriter
import com.lowagie.text.pdf.draw.VerticalPositionMark
import fi.hel.haitaton.hanke.getResource
import java.awt.Color
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.io.ByteArrayOutputStream
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.print.PrintTranscoder

private val baseRegularFont =
    BaseFont.createFont(
        "pdf-assets/liberation-sans/LiberationSans-Regular.ttf",
        BaseFont.IDENTITY_H,
        BaseFont.EMBEDDED,
    )
private val baseBoldFont =
    BaseFont.createFont(
        "pdf-assets/liberation-sans/LiberationSans-Bold.ttf",
        BaseFont.IDENTITY_H,
        BaseFont.EMBEDDED,
    )

/**
 * A4 paper size in figma is 1190 x 1684 px.
 *
 * In PDF, it's 595 x 842 pt.
 *
 * So when converting px to pt, we need to multiply by 595/1190 = 0.5 = 842/1684.
 */
const val PX_TO_PT = 0.5f

/**
 * Font used for the top header of document, text above the main title. The biggest title we have.
 */
private val headerFont = Font(baseBoldFont, pxToPt(18), Font.NORMAL, Color.BLACK)

/** Font used for the main title of document. The biggest title we have. */
private val titleFont = Font(baseRegularFont, pxToPt(32), Font.NORMAL, Color.BLACK)
/** Font used for subtitle under the main title. */
private val subtitleFont = Font(baseRegularFont, pxToPt(20), Font.NORMAL, Color.BLACK)
/** Font used for the title of a section of data. */
private val sectionTitleFont = Font(baseBoldFont, pxToPt(20), Font.NORMAL, Color.BLACK)

/** Font used for the left header column of a data section. */
private val rowHeaderFont = Font(baseBoldFont, pxToPt(14), Font.NORMAL, Color.BLACK)
/** Font used for the left header column of a data section. */
private val textFont = Font(baseRegularFont, pxToPt(14), Font.NORMAL, Color.BLACK)

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

fun PdfPTable.rowIfNotBlank(title: String, content: String?) {
    if (!content.isNullOrBlank()) {
        row(title, content)
    }
}

fun Document.section(sectionTitle: String, addRows: PdfPTable.() -> Unit) {
    val paragraph = Paragraph()
    paragraph.keepTogether = true
    paragraph.spacingBefore = pxToPt(-39)
    paragraph.add(Paragraph(sectionTitle, sectionTitleFont))

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

    table.addRows()
    paragraph.add(table)
    this.add(paragraph)
}

fun createDocument(addContent: (Document, PdfWriter) -> Unit): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val padding = pxToPt(40)
    val document = Document(PageSize.A4, padding, padding, padding / 2, padding)
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
