package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Chunk
import com.lowagie.text.Document
import com.lowagie.text.Image
import com.lowagie.text.ImgTemplate
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.tormaystarkastelu.Autoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.awt.Color
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
        val hankealueIds = data.areas!!.map { it.hankealueId }.toSet()
        val hankealueet = hanke.alueet.filter { hankealueIds.contains(it.id) }

        document.headerRow(
            "${hanke.nimi} (${hanke.hankeTunnus})",
            rightSide = ZonedDateTime.now().format()!!.trim(),
        )

        document.title("Haittojenhallintasuunnitelma")
        document.subtitle(data.name)

        document.mapHeader("Alueiden sijainti", locationIcon)
        document.map(data.areas, hankealueet)

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

        hankealueet.forEach { hankealue ->
            val kaivuilmoitusalue = data.areas.first { it.hankealueId == hankealue.id }
            val worstIndexes = kaivuilmoitusalue.worstCasesInTormaystarkastelut()

            document.section(hankealue.nimi) {
                hankealueRow(
                    hankealue,
                    hankealue.tormaystarkasteluTulos?.liikennehaittaindeksi?.indeksi,
                    Haittojenhallintatyyppi.YLEINEN,
                    "Toimet työalueiden haittojen hallintaan, ${hankealue.nimi}",
                )

                row(toimetTitle())
                row(nuisanceScore(worstIndexes?.liikennehaittaindeksi?.indeksi))
                val toimet =
                    kaivuilmoitusalue.haittojenhallintasuunnitelma[Haittojenhallintatyyppi.YLEINEN]
                row(toimet(toimet))
                emptyRow()
            }

            document.section(null) {
                row(
                    "Linja-autojen paikallisliikenne, ${hankealue.nimi}",
                    map(kaivuilmoitusalue, hankealue) { it?.linjaautoliikenneindeksi },
                )

                hankealueRow(
                    hankealue,
                    hankealue.tormaystarkasteluTulos?.linjaautoliikenneindeksi,
                    Haittojenhallintatyyppi.LINJAAUTOLIIKENNE,
                )
                emptyRow()

                row(toimetTitle())
                row(nuisanceScore(worstIndexes?.linjaautoliikenneindeksi))
                val toimet =
                    kaivuilmoitusalue.haittojenhallintasuunnitelma[
                            Haittojenhallintatyyppi.LINJAAUTOLIIKENNE]
                row(toimet(toimet))
                emptyRow()
            }

            document.section(null) {
                row(
                    "Autoliikenteen ruuhkautuminen, ${hankealue.nimi}",
                    map(kaivuilmoitusalue, hankealue) { it?.autoliikenne?.indeksi },
                )

                hankealueRow(
                    hankealue,
                    hankealue.tormaystarkasteluTulos?.autoliikenne?.indeksi,
                    Haittojenhallintatyyppi.AUTOLIIKENNE,
                )
                emptyRow()

                row(toimetTitle())
                row(autoliikennehaitat(worstIndexes?.autoliikenne))
                val toimet =
                    kaivuilmoitusalue.haittojenhallintasuunnitelma[
                            Haittojenhallintatyyppi.AUTOLIIKENNE]
                row(toimet(toimet))
                emptyRow()
            }

            document.section(null) {
                row(
                    "Pyöräliikenteen merkittävyys, ${hankealue.nimi}",
                    map(kaivuilmoitusalue, hankealue) { it?.pyoraliikenneindeksi },
                )

                hankealueRow(
                    hankealue,
                    hankealue.tormaystarkasteluTulos?.pyoraliikenneindeksi,
                    Haittojenhallintatyyppi.PYORALIIKENNE,
                )
                emptyRow()

                row(toimetTitle())
                row(nuisanceScore(worstIndexes?.pyoraliikenneindeksi))
                val toimet =
                    kaivuilmoitusalue.haittojenhallintasuunnitelma[
                            Haittojenhallintatyyppi.PYORALIIKENNE]
                row(toimet(toimet))
                emptyRow()
            }

            document.section(null) {
                row(
                    "Raitioliikenne, ${hankealue.nimi}",
                    map(kaivuilmoitusalue, hankealue) { it?.raitioliikenneindeksi },
                )

                hankealueRow(
                    hankealue,
                    hankealue.tormaystarkasteluTulos?.raitioliikenneindeksi,
                    Haittojenhallintatyyppi.RAITIOLIIKENNE,
                )
                emptyRow()

                row(toimetTitle())
                row(nuisanceScore(worstIndexes?.raitioliikenneindeksi))
                val toimet =
                    kaivuilmoitusalue.haittojenhallintasuunnitelma[
                            Haittojenhallintatyyppi.RAITIOLIIKENNE]
                row(toimet(toimet))
                emptyRow()
            }

            document.section(null) {
                hankealueRow(
                    hankealue,
                    null,
                    Haittojenhallintatyyppi.MUUT,
                    "Muut haittojenhallintatoimet, ${hankealue.nimi}",
                )

                row(muutHaitat(kaivuilmoitusalue))
                row(toimetTitle())
                val toimet =
                    kaivuilmoitusalue.haittojenhallintasuunnitelma[Haittojenhallintatyyppi.MUUT]
                row(toimet(toimet))
                emptyRow()
            }
        }
    }

    private fun PdfPTable.hankealueRow(
        hankealue: SavedHankealue,
        indeksi: Float?,
        ryhma: Haittojenhallintatyyppi,
        rowTitle: String? = null,
    ) {
        if (rowTitle != null) {
            addCell(Phrase(rowTitle, rowHeaderFont))
        } else {
            addCell(defaultCell)
        }

        val hankealueCell = PdfPCell(defaultCell)
        hankealueCell.border = Rectangle.BOX
        hankealueCell.borderWidth = pxToPt(1)
        hankealueCell.borderColor = Color.BLACK
        hankealueCell.backgroundColor = HANKEALUE_COLOR
        hankealueCell.setPadding(pxToPt(16))
        hankealueCell.isUseBorderPadding = false
        hankealueCell.paddingTop = pxToPt(0)

        hankealueCell.addElement(Phrase("Hankealueen haittojen hallinta", toimetFont))
        hankealueCell.addElement(Phrase(pxToPt(16), Chunk.NEWLINE))

        indeksi?.let {
            hankealueCell.addElement(nuisanceScore(it, "Hankealueen haittaindeksi"))
            hankealueCell.addElement(Phrase(pxToPt(16), Chunk.NEWLINE))
        }

        hankealueCell.addElement(toimet(hankealue.haittojenhallintasuunnitelma?.get(ryhma)))

        addCell(hankealueCell)
    }

    private fun toimetTitle(): Phrase =
        Phrase("Toimet työalueiden haittojen hallintaan", toimetFont)

    private fun toimet(toimet: String?): Paragraph {
        val kuvaus =
            toimet?.ifBlank { null } ?: "Haitaton ei ole tunnistanut hankealueelta tätä kohderyhmää"

        return Paragraph(kuvaus, textFont)
    }

    private fun autoliikennehaitat(haitat: Autoliikenneluokittelu?): Paragraph {
        val p = Paragraph()
        p.add(nuisanceScore(haitat?.indeksi))
        p.add(Chunk.NEWLINE)

        p.add(nuisanceScore(haitat?.katuluokka, "Katuluokka"))
        p.add(Chunk.NEWLINE)

        p.add(nuisanceScore(haitat?.kaistahaitta, "Vaikutus autoliikenteen kaistamääriin"))
        p.add(Chunk.NEWLINE)

        p.add(nuisanceScore(haitat?.kaistapituushaitta, "Autoliikenteen kaistavaikutusten pituus"))
        p.add(Chunk.NEWLINE)

        p.add(nuisanceScore(haitat?.haitanKesto?.toFloat(), "Työn kesto"))
        p.spacingAfter = 0f

        return p
    }

    private fun muutHaitat(kaivuilmoitusAlue: KaivuilmoitusAlue): Paragraph {
        val p = Paragraph()
        p.add(nuisanceScore(kaivuilmoitusAlue.meluhaitta.value, "Melu", NuisanceColor.LAVENDER))
        p.add(Chunk.NEWLINE)

        p.add(nuisanceScore(kaivuilmoitusAlue.polyhaitta.value, "Pöly", NuisanceColor.LAVENDER))
        p.add(Chunk.NEWLINE)

        p.add(nuisanceScore(kaivuilmoitusAlue.tarinahaitta.value, "Tärinä", NuisanceColor.LAVENDER))
        p.spacingAfter = 0f
        return p
    }

    fun Document.map(areas: List<KaivuilmoitusAlue>, hankealueet: List<SavedHankealue>) {
        // The image is better quality, if it's rendered at a higher resolution and then scaled down
        // to fit in the PDF layout.
        val bytes =
            mapGenerator.mapWithAreas(
                areas,
                hankealueet,
                HEADER_MAP_WIDTH * 2,
                HEADER_MAP_HEIGHT * 2,
            ) {
                it?.liikennehaittaindeksi?.indeksi
            }
        val image = Image.getInstance(bytes)
        image.scaleToFit(pxToPt(HEADER_MAP_WIDTH), pxToPt(HEADER_MAP_HEIGHT))
        this.add(image)
    }

    fun map(
        area: KaivuilmoitusAlue,
        hankealue: SavedHankealue,
        selectIndex: (TormaystarkasteluTulos?) -> Float?,
    ): Image {
        // The image is better quality, if it's rendered at a higher resolution and then scaled down
        // to fit in the PDF layout.
        val bytes =
            mapGenerator.mapWithAreas(
                listOf(area),
                listOf(hankealue),
                COLUMN_MAP_WIDTH * 2,
                COLUMN_MAP_HEIGHT * 2,
                selectIndex,
            )
        val image = Image.getInstance(bytes)
        image.scaleToFit(pxToPt(COLUMN_MAP_WIDTH), pxToPt(COLUMN_MAP_HEIGHT))
        return image
    }

    companion object {
        const val HEADER_MAP_WIDTH = 1110
        const val HEADER_MAP_HEIGHT = 400

        const val COLUMN_MAP_WIDTH = 860
        const val COLUMN_MAP_HEIGHT = 304

        val HANKEALUE_COLOR = Color(0xFF, 0xF4, 0xD4)
    }
}
