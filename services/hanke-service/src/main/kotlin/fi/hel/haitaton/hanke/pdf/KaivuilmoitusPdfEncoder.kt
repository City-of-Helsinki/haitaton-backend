package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Document
import com.lowagie.text.PageSize
import com.lowagie.text.pdf.PdfWriter
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.io.ByteArrayOutputStream

object KaivuilmoitusPdfEncoder {

    fun createPdf(
        data: KaivuilmoitusData,
        totalArea: Float?,
        attachments: List<ApplicationAttachmentMetadata>,
        areas: List<EnrichedKaivuilmoitusalue>?,
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val document = Document(PageSize.A4)
        PdfWriter.getInstance(document, outputStream)
        formatKaivuilmoitusPdf(
            document,
            data,
            totalArea,
            attachments,
            areas,
        )
        return outputStream.toByteArray()
    }

    private fun formatKaivuilmoitusPdf(
        document: Document,
        data: KaivuilmoitusData,
        totalArea: Float?,
        attachments: List<ApplicationAttachmentMetadata>,
        areas: List<EnrichedKaivuilmoitusalue>?,
    ) {
        document.open()

        document.title("Kaivuilmoitus")

        document.section("Perustiedot") {
            row("Työn nimi", data.name)
            row("Työn kuvaus", data.workDescription)
            row("Työssä on kyse", data.getWorkTargets())
            row("Tehtyjen johtoselvitysten tunnukset", data.cableReports.format())
            if (!data.cableReportDone) {
                row(
                    "Uusi johtoselvitys",
                    "Louhitaanko työn yhteydessä, esimerkiksi maaperää? ${data.rockExcavation.format()}")
            }
            row("Sijoitussopimustunnukset", data.placementContracts.format())
            row("Työhön vaadittavat pätevyydet", data.requiredCompetence.format())
            row("Tilaaja", data.getOrderer()?.format() ?: "-")
        }

        document.newPage()

        document.section("Alueet") {
            row("Alueiden kokonaispinta-ala", totalArea.format() + " m²")
            row("Työn arvioitu alkupäivä", data.startTime.format())
            row("Työn arvioitu loppupäivä", data.endTime.format())
            row("Alueet", areas?.joinToString("\n\n") { it.format() })
        }

        document.newPage()

        document.section("Yhteystiedot") {
            data.customerWithContacts?.let { row("Työstä vastaava", it.format()) }
            data.contractorWithContacts?.let { row("Työn suorittaja", it.format()) }
            data.propertyDeveloperWithContacts?.let { row("Rakennuttaja", it.format()) }
            data.representativeWithContacts?.let { row("Asianhoitaja", it.format()) }
        }
        document.newPage()

        document.section("Laskutustiedot") {
            data.invoicingCustomer?.let {
                row("Nimi", it.nimi)
                rowIfNotBlank("Yksilöivä tunnus", it.registryKey)

                if (!it.katuosoite.isNullOrBlank()) {
                    row(
                        "Osoite",
                        "${it.katuosoite}\n${it.postinumero.orDash()} ${it.postitoimipaikka.orDash()}")
                }

                row("OVT-tunnus", it.ovttunnus.orDash())
                row("Välittäjän tunnus", it.valittajanTunnus.orDash())
                row("Puhelinnumero", it.puhelinnumero.orDash())
                row("Sähköposti", it.sahkoposti.orDash())
                row("Asiakkaan viite", it.asiakkaanViite.orDash())
            }
        }
        document.newPage()

        document.section("Liitteet ja lisätiedot") {
            val attachmentsByType = attachments.groupBy { it.attachmentType }
            row(
                "Tilapäisiä liikennejärjestelyitä koskevat suunnitelmat",
                attachmentsByType[ApplicationAttachmentType.LIIKENNEJARJESTELY]?.joinToString(
                    "\n") {
                        it.fileName
                    } ?: "")
            row(
                "Valtakirjat",
                attachmentsByType[ApplicationAttachmentType.VALTAKIRJA]?.joinToString("\n") {
                    it.fileName
                } ?: "")
            row(
                "Muut liitteet",
                attachmentsByType[ApplicationAttachmentType.MUU]?.joinToString("\n") { it.fileName }
                    ?: "")
            row("Lisätietoja hakemuksesta", data.additionalInfo.orDash())
        }

        document.close()
    }

    private fun KaivuilmoitusData.getOrderer(): Hakemusyhteyshenkilo? =
        listOfNotNull(
                customerWithContacts,
                contractorWithContacts,
                representativeWithContacts,
                propertyDeveloperWithContacts)
            .flatMap { it.yhteyshenkilot }
            .find { it.tilaaja }

    private fun KaivuilmoitusData.getWorkTargets(): String =
        listOf(
                constructionWork to "Uuden rakenteen tai johdon rakentamisesta",
                maintenanceWork to "Olemassaolevan rakenteen kunnossapitotyöstä",
                emergencyWork to
                    "Kaivutyö on aloitettu ennen kaivuilmoituksen tekemistä merkittävien vahinkojen välttämiseksi",
            )
            .filter { (active, _) -> active }
            .joinToString("\n") { (_, description) -> description }

    private fun EnrichedKaivuilmoitusalue.format(): String {
        val builder = StringBuilder()
        builder.append("Työalueet ($hankealueName)\n")
        builder.append("\n")
        alue.tyoalueet.forEachIndexed { i, tyoalue ->
            builder.append("Työalue ${i+1}: (${tyoalue.area.format()} m²)\n")
        }
        builder.append("\n")

        builder.append("Pinta-ala: ${totalArea.format()} m²\n")
        builder.append("Katuosoite: ${alue.katuosoite}\n")
        builder.append(
            "Työn tarkoitus: ${alue.tyonTarkoitukset.map { it.format() }.joinToString ( ", " )}\n")
        builder.append("\n")

        builder.append("Meluhaitta: ${alue.meluhaitta.format()}\n")
        builder.append("Pölyhaitta: ${alue.polyhaitta.format()}\n")
        builder.append("Tärinähaitta: ${alue.tarinahaitta.format()}\n")
        builder.append("Autoliikenteen kaistahaitta: ${alue.kaistahaitta.format()}\n")
        builder.append("Kaistahaittojen pituus: ${alue.kaistahaittojenPituus.format()}\n")
        builder.append("\n")

        builder.append("Lisätietoja alueesta: ${alue.lisatiedot.orDash()}\n")
        return builder.toString()
    }
}
