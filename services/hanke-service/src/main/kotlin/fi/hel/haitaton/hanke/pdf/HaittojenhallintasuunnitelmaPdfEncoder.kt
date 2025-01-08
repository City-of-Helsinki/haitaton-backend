package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Document
import com.lowagie.text.ImgTemplate
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.time.ZonedDateTime

object HaittojenhallintasuunnitelmaPdfEncoder {

    fun createPdf(hanke: Hanke, data: KaivuilmoitusData, totalArea: Float?): ByteArray =
        createDocument { document, writer ->
            val locationIcon = loadLocationIcon(writer)
            formatKaivuilmoitusPdf(document, hanke, data, totalArea, locationIcon)
        }

    private fun formatKaivuilmoitusPdf(
        document: Document,
        hanke: Hanke,
        data: KaivuilmoitusData,
        totalArea: Float?,
        locationIcon: ImgTemplate,
    ) {
        document.headerRow(
            "${hanke.nimi} (${hanke.hankeTunnus})",
            rightSide = ZonedDateTime.now().format()!!.trim(),
        )

        document.title("Haittojenhallintasuunnitelma")
        document.subtitle(data.name)

        document.mapHeader("Alueiden sijainti", locationIcon)
        document.placeholderImage()

        document.section("Perustiedot") {
            row("Työn nimi", data.name)
            row("Työn kuvaus", data.workDescription)
            row("Työssä on kyse", data.getWorkTargets())
            row("Tehtyjen johtoselvitysten tunnukset", data.cableReports.format())
            if (!data.cableReportDone) {
                val excavation =
                    "Louhitaanko työn yhteydessä, esimerkiksi kallioperää?: ${data.rockExcavation.format()}"
                val placeholder = "Työssä on kyse: Olemassaolevan rakenteen kunnossapitotyöstä"
                row("Uusi johtoselvitys", "$excavation\n$placeholder")
            }
            row("Sijoitussopimustunnukset", data.placementContracts.format())
            row("Työhön vaadittavat pätevyydet", data.requiredCompetence.format())
        }

        document.section("Alueet") {
            row("Alueiden kokonaispinta-ala", totalArea.format() + " m²")
            row("Työn alkupäivämäärä", data.startTime.format())
            row("Työn loppupäivämäärä", data.endTime.format())
        }
    }
}
