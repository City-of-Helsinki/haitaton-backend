package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Image
import com.lowagie.text.ImgTemplate
import com.lowagie.text.Paragraph
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.time.ZonedDateTime
import org.springframework.stereotype.Component

@Component
class HaittojenhallintasuunnitelmaPdfEncoder(private val mapGenerator: MapGenerator) {

    fun createPdf(hanke: Hanke, data: KaivuilmoitusData, totalArea: Float?): ByteArray =
        createDocument { document, writer ->
            val locationIcon = loadLocationIcon(writer)
            formatPdf(document, hanke, data, totalArea, locationIcon)
        }

    private fun formatPdf(
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
        document.map(data.areas!!, hanke.alueet)

        val spacer = Paragraph(Chunk.NEWLINE)
        spacer.spacingBefore = 1f
        spacer.spacingAfter = pxToPt(-16)
        document.add(spacer)

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

    fun Document.map(areas: List<KaivuilmoitusAlue>, hankealueet: List<SavedHankealue>) {
        // The image is better quality, if it's rendered at a higher resolution and then scaled down
        // to fit in the PDF layout.
        val bytes = mapGenerator.mapWithAreas(areas, hankealueet, MAP_WIDTH * 2, MAP_HEIGHT * 2)
        val image = Image.getInstance(bytes)
        image.scaleToFit(pxToPt(MAP_WIDTH), pxToPt(MAP_HEIGHT))
        this.add(image)
    }

    companion object {
        const val MAP_WIDTH = 1110
        const val MAP_HEIGHT = 400
    }
}
