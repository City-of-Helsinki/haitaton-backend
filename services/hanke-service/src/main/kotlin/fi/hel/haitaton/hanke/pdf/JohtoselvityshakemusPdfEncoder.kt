package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Document
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData

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
    ): ByteArray = createDocument { document, _ ->
        formatJohtoselvitysPdf(document, data, totalArea, areas, attachments)
    }

    private fun formatJohtoselvitysPdf(
        document: Document,
        data: JohtoselvityshakemusData,
        totalArea: Float?,
        areas: List<Float?>,
        attachments: List<ApplicationAttachmentMetadata>,
    ) {

        document.title("Johtoselvityshakemus")
        document.subtitle(data.name)

        document.section("Perustiedot") {
            row("Työn nimi", data.name)
            row("Osoitetiedot", data.postalAddress?.format())
            row("Työssä on kyse", data.getWorkTargets())
            row("Työn kuvaus", data.workDescription)
            row("Omat tiedot", data.getOrderer()?.format())
        }

        document.section("Alueet") {
            val areaNames =
                data.areas?.mapIndexed { i, area -> area.name.ifBlank { "Työalue ${i+1}" } }
                    ?: listOf()

            row("Työn arvioitu alkupäivä", data.startTime.format())
            row("Työn arvioitu loppupäivä", data.endTime.format())
            row("Alueiden kokonaispinta-ala", totalArea.format() + " m²")
            row(
                "Alueet",
                areas
                    .mapIndexed { i, area -> "${areaNames[i]}\n\nPinta-ala: ${area.format()} m²" }
                    .joinToString("\n\n"),
            )
        }

        document.section("Yhteystiedot") {
            if (data.customerWithContacts != null) {
                row("Työstä vastaava", data.customerWithContacts.format())
            }
            if (data.contractorWithContacts != null) {
                row("Työn suorittaja", data.contractorWithContacts.format())
            }
            if (data.propertyDeveloperWithContacts != null) {
                row("Rakennuttaja", data.propertyDeveloperWithContacts.format())
            }
            if (data.representativeWithContacts != null) {
                row("Asianhoitaja", data.representativeWithContacts.format())
            }
            data.paperDecisionReceiver?.let { row("Päätös tilattu paperisena", it.format()) }
        }

        document.section("Liitteet") {
            if (attachments.isNotEmpty()) {
                row("Lisätyt liitetiedostot", attachments.joinToString("\n") { it.fileName })
            }
        }
    }

    private fun JohtoselvityshakemusData.getOrderer(): Hakemusyhteyshenkilo? =
        listOfNotNull(
                customerWithContacts,
                contractorWithContacts,
                representativeWithContacts,
                propertyDeveloperWithContacts,
            )
            .flatMap { it.yhteyshenkilot }
            .find { it.tilaaja }

    private fun JohtoselvityshakemusData.getWorkTargets(): String =
        listOf(
                constructionWork to "Uuden rakenteen tai johdon rakentamisesta",
                maintenanceWork to "Olemassaolevan rakenteen kunnossapitotyöstä",
                emergencyWork to
                    "Kaivutyö on aloitettu ennen johtoselvityksen tilaamista merkittävien vahinkojen välttämiseksi",
                propertyConnectivity to "Kiinteistöliittymien rakentamisesta",
            )
            .filter { (active, _) -> active }
            .joinToString("\n") { (_, description) -> description }
}
