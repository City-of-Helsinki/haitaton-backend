package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Document
import com.lowagie.text.PageSize
import com.lowagie.text.pdf.PdfWriter
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import java.io.ByteArrayOutputStream

/**
 * Transform an application to a PDF. The PDF is added as an attachment when sending the application
 * to Allu. It will be archived along the application and decision to show how the user inputted the
 * application in Haitaton, as the data models of Haitaton and Allu differ slightly.
 */
object JohtoselvityshakemusPdfEncoder {

    fun createPdf(
        data: JohtoselvityshakemusData,
        totalArea: Float?,
        areas: List<Float?>,
        attachments: List<ApplicationAttachmentMetadata>,
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4)
        PdfWriter.getInstance(document, outputStream)
        formatPdf(
            document,
            data,
            totalArea,
            areas,
            attachments,
        )
        return outputStream.toByteArray()
    }

    private fun formatPdf(
        document: Document,
        data: JohtoselvityshakemusData,
        totalArea: Float?,
        areas: List<Float?>,
        attachments: List<ApplicationAttachmentMetadata>,
    ) {
        document.open()

        document.title("Johtoselvityshakemus")

        document.section("Perustiedot") { table ->
            table.row("Työn nimi", data.name)
            table.row("Osoitetiedot", data.postalAddress?.format())
            table.row("Työssä on kyse", data.getWorkTargets())
            table.row("Työn kuvaus", data.workDescription)
            table.row("Omat tiedot", data.getOrderer()?.format())
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
            if (data.customerWithContacts != null) {
                table.row("Työstä vastaavat", data.customerWithContacts.format())
            }
            if (data.contractorWithContacts != null) {
                table.row("Työn suorittajat", data.contractorWithContacts.format())
            }
            if (data.propertyDeveloperWithContacts != null) {
                table.row("Rakennuttajat", data.propertyDeveloperWithContacts.format())
            }
            if (data.representativeWithContacts != null) {
                table.row("Asianhoitajat", data.representativeWithContacts.format())
            }
        }

        document.newPage()

        document.section("Liitteet") { table ->
            if (attachments.isNotEmpty()) {
                table.row(
                    "Lisätyt liitetiedostot",
                    attachments.map { it.fileName }.joinToString("\n")
                )
            }
        }

        document.close()
    }

    private fun JohtoselvityshakemusData.getOrderer(): Hakemusyhteyshenkilo? =
        customerWithContacts?.yhteyshenkilot?.find { it.tilaaja }
            ?: contractorWithContacts?.yhteyshenkilot?.find { it.tilaaja }
            ?: representativeWithContacts?.yhteyshenkilot?.find { it.tilaaja }
            ?: propertyDeveloperWithContacts?.yhteyshenkilot?.find { it.tilaaja }

    private fun JohtoselvityshakemusData.getWorkTargets(): String =
        listOf(
                constructionWork to "Uuden rakenteen tai johdon rakentamisesta",
                maintenanceWork to "Olemassaolevan rakenteen kunnossapitotyöstä",
                emergencyWork to
                    "Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien vahinkojen välttämiseksi",
                propertyConnectivity to "Kiinteistöliittymien rakentamisesta"
            )
            .filter { (active, _) -> active }
            .joinToString("\n") { (_, description) -> description }
}
