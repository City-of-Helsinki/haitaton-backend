package fi.hel.haitaton.hanke.allu

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Transform an application to a PDF. The PDF is added as an attachment when sending the application
 * to Allu. It will be archived along the application and decision to show how the user inputted the
 * application in Haitaton, as the data models of Haitaton and Allu differ slightly.
 */
object ApplicationPdfService {
    private val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
    private val sectionFont = Font(Font.HELVETICA, 15f, Font.BOLD)

    private val headerFont = Font(Font.HELVETICA, 10f, Font.BOLD)
    private val textFont = Font(Font.HELVETICA, 10f, Font.NORMAL)

    private val finnishDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.uuuu")

    private fun Document.newline() {
        this.add(Paragraph(Chunk.NEWLINE))
    }

    private fun Document.title(title: String) {
        val paragraph = Paragraph(title, titleFont)
        paragraph.alignment = Element.ALIGN_CENTER
        this.add(paragraph)
        this.newline()
    }

    private fun Document.sectionTitle(sectionTitle: String) {
        val paragraph = Paragraph(sectionTitle, sectionFont)
        this.add(paragraph)
        this.newline()
    }

    fun PdfPTable.row(key: String, value: Any?) {
        this.addCell(Phrase("$key ", headerFont))
        this.addCell(Phrase(value?.toString() ?: "<Tyhjä>", textFont))
    }

    private fun Document.section(sectionTitle: String, addRows: (table: PdfPTable) -> Unit) {
        this.sectionTitle(sectionTitle)

        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.setWidths(floatArrayOf(1.5f, 3.5f))
        table.setSpacingBefore(10f)
        table.defaultCell.border = Rectangle.NO_BORDER
        table.defaultCell.paddingBottom = 15f

        addRows(table)

        this.add(table)
    }

    private fun PostalAddress.format(): String =
        "${this.streetAddress.streetName}\n${this.postalCode} ${this.city}"

    private fun Contact.format(): String =
        listOfNotNull(this.fullName(), this.email, this.phone)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun CustomerWithContacts.format(): String =
        listOfNotNull(
                this.customer.name + "\n",
                this.customer.registryKey,
                this.customer.email,
                this.customer.phone,
                "\nYhteyshenkilöt\n",
            )
            .filter { it.isNotBlank() }
            .joinToString("\n") + this.contacts.joinToString("\n") { "\n" + it.format() }

    private fun ZonedDateTime?.format(): String? =
        this?.withZoneSameInstant(ZoneId.of("Europe/Helsinki"))?.format(finnishDateFormat)

    fun createPdf(
        data: CableReportApplicationData,
        totalArea: Float?,
        areas: List<Float?>,
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4)
        val pdfWriter = PdfWriter.getInstance(document, outputStream)
        formatPdf(
            document,
            data,
            totalArea,
            areas,
        )
        pdfWriter.close()
        return outputStream.toByteArray()
    }

    private fun formatPdf(
        document: Document,
        data: CableReportApplicationData,
        totalArea: Float?,
        areas: List<Float?>,
    ) {
        document.open()

        document.title("Johtoselvityshakemus")

        document.section("Perustiedot") { table ->
            table.row("Työn nimi", data.name)
            table.row("Osoitetiedot", data.postalAddress?.format())
            table.row("Työssä on kyse", getWorkTargets(data))
            table.row("Työn kuvaus", data.workDescription)
            table.row("Omat tiedot", getOrderer(data)?.format())
        }

        document.newPage()

        document.section("Alueet") { table ->
            val areaNames =
                data.areas?.mapIndexed { i, area -> area.name.ifBlank { "Työalue ${i+1}" } }
                    ?: listOf()

            table.row("Työn arvioitu alkupäivä", data.startTime.format())
            table.row("Työn arvioitu loppupäivä", data.endTime.format())
            table.row("Alueiden kokonaispinta-ala", totalArea.toString() + " m²")
            table.row(
                "Alueet",
                areas
                    .mapIndexed { i, area -> "${areaNames[i]}\n\nPinta-ala: $area m²" }
                    .joinToString("\n\n")
            )
        }

        document.newPage()

        document.section("Yhteystiedot") { table ->
            table.row("Työstä vastaavat", data.customerWithContacts.format())
            table.row("Työn suorittajat", data.contractorWithContacts.format())
            if (data.propertyDeveloperWithContacts != null) {
                table.row("Rakennuttajat", data.propertyDeveloperWithContacts.format())
            }
            if (data.representativeWithContacts != null) {
                table.row("Asianhoitajat", data.representativeWithContacts.format())
            }
        }

        document.close()
    }

    private fun getOrderer(data: CableReportApplicationData): Contact? =
        data.customerWithContacts.contacts.find { it.orderer }
            ?: data.contractorWithContacts.contacts.find { it.orderer }
                ?: data.representativeWithContacts?.contacts?.find { it.orderer }
                ?: data.propertyDeveloperWithContacts?.contacts?.find { it.orderer }

    private fun getWorkTargets(applicationData: CableReportApplicationData): String {
        return listOf(
                applicationData.constructionWork to "Uuden rakenteen tai johdon rakentamisesta",
                applicationData.maintenanceWork to "Olemassaolevan rakenteen kunnossapitotyöstä",
                applicationData.emergencyWork to
                    "Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien vahinkojen välttämiseksi",
                applicationData.propertyConnectivity to "Kiinteistöliittymien rakentamisesta"
            )
            .filter { (active, _) -> active }
            .joinToString("\n") { (_, description) -> description }
    }
}
