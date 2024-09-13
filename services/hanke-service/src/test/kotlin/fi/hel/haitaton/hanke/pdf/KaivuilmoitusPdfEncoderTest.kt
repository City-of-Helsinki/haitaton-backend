package fi.hel.haitaton.hanke.pdf

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsMatch
import assertk.assertions.doesNotContain
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.Tyoalue
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KaivuilmoitusPdfEncoderTest {

    @Nested
    inner class CreatePdf {
        @Test
        fun `created PDF contains title and section headers`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData()

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData))
                .contains(
                    "Kaivuilmoitus",
                    "Perustiedot",
                    "Alueet",
                    "Yhteystiedot",
                    "Liitteet ja lisätiedot",
                )
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData(cableReportDone = false)

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työn nimi")
                contains("Työn kuvaus")
                contains("Työssä on kyse")
                hasPhrase("Tehtyjen johtoselvitysten tunnukset")
                contains("Uusi johtoselvitys")
                contains("Sijoitussopimustunnukset")
                contains("Työhön vaadittavat pätevyydet")
                contains("Tilaaja")
            }
        }

        @Test
        fun `created PDF contains basic information`() {
            val applicationData =
                HakemusFactory.createKaivuilmoitusData(
                    constructionWork = true,
                    maintenanceWork = true,
                    emergencyWork = true,
                    cableReports = listOf("JS2400001", "JS2400002"),
                    cableReportDone = false,
                    rockExcavation = false,
                    placementContracts = listOf("Sopimus1", "Sopimus2"),
                    requiredCompetence = true,
                    contractorWithContacts =
                        HakemusyhteystietoFactory.create()
                            .withYhteyshenkilo(
                                etunimi = "Tapio",
                                sukunimi = "Tilaaja",
                                sahkoposti = "tapio@tilaaja.test",
                                puhelin = "09876543",
                                tilaaja = true,
                            ))

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(applicationData, 1f, listOf(), listOf())

            val pdfText = getPdfAsText(pdfData)
            assertThat(pdfText).all {
                contains(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                contains(ApplicationFactory.DEFAULT_WORK_DESCRIPTION)
                contains("Uuden rakenteen tai johdon rakentamisesta")
                contains("Olemassaolevan rakenteen kunnossapitotyöstä")
                hasPhrase(
                    "Kaivutyö on aloitettu ennen kaivuilmoituksen tekemistä " +
                        "merkittävien vahinkojen välttämiseksi")
                contains("JS2400001, JS2400002")
                hasPhrase("Louhitaanko työn yhteydessä, esimerkiksi maaperää? Ei")
                contains("Sopimus1, Sopimus2")
                contains("Kyllä")
                hasPhrase("Tapio Tilaaja tapio@tilaaja.test 09876543")
            }
        }

        @Test
        fun `created PDF doesn't have this work involves fields if none are selected`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    constructionWork = false,
                    maintenanceWork = false,
                    emergencyWork = false,
                )

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                doesNotContain("Uuden rakenteen tai johdon rakentamisesta")
                doesNotContain("Olemassaolevan rakenteen kunnossapitotyöstä")
                doesNotContain("välttämiseksi")
            }
        }

        @Test
        fun `created PDF contains headers for area information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData()

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Alueiden kokonaispinta-ala")
                contains("Työn arvioitu alkupäivä")
                contains("Työn arvioitu loppupäivä")
                contains("Alueet")
            }
        }

        @Test
        fun `created PDF contains area information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                    areas = listOf())
            val area =
                EnrichedKaivuilmoitusalue(
                    223.41411f,
                    "Ensimmäinen hankealue",
                    KaivuilmoitusAlue(
                        name = "Hakemusalue",
                        hankealueId = 11,
                        tyoalueet =
                            listOf(
                                Tyoalue(GeometriaFactory.secondPolygon(), 147.5799, null),
                                Tyoalue(GeometriaFactory.secondPolygon(), 75.83421, null),
                            ),
                        katuosoite = "Hakemusalueen osoite",
                        tyonTarkoitukset = setOf(TyomaaTyyppi.VESI, TyomaaTyyppi.KAASUJOHTO),
                        meluhaitta = Meluhaitta.TOISTUVA_MELUHAITTA,
                        polyhaitta = Polyhaitta.SATUNNAINEN_POLYHAITTA,
                        tarinahaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
                        kaistahaitta = VaikutusAutoliikenteenKaistamaariin.EI_VAIKUTA,
                        kaistahaittojenPituus =
                            AutoliikenteenKaistavaikutustenPituus.EI_VAIKUTA_KAISTAJARJESTELYIHIN,
                        lisatiedot = "Lisätiedot hakemusalueesta.",
                    ))

            val pdfData =
                KaivuilmoitusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf(area))

            assertThat(getPdfAsText(pdfData)).all {
                contains("614,00 m²")
                contains("18.11.2022")
                contains("28.11.2022")
                contains("Työalueet (Ensimmäinen hankealue)")
                contains("Työalue 1: (147,58 m²)")
                contains("Työalue 2: (75,83 m²)")
                contains("Pinta-ala: 223,41 m²")
                contains("Katuosoite: Hakemusalueen osoite")
                contains("Työn tarkoitus: Vesi, Kaasujohto")
                contains("Meluhaitta: Toistuva meluhaitta")
                contains("Pölyhaitta: Satunnainen pölyhaitta")
                contains("Tärinähaitta: Jatkuva tärinähaitta")
                contains("Kaistahaittojen pituus: Ei vaikuta")
                contains("Lisätietoja alueesta: Lisätiedot hakemusalueesta.")
            }
        }

        @Test
        fun `contains headers for contact information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    representativeWithContacts =
                        HakemusyhteystietoFactory.createPerson().withYhteyshenkilo(),
                    propertyDeveloperWithContacts =
                        HakemusyhteystietoFactory.create().withYhteyshenkilo())

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työstä vastaava")
                contains("Työn suorittaja")
                contains("Rakennuttaja")
                contains("Asianhoitaja")
                contains("Yhteyshenkilöt")
            }
        }

        @Test
        fun `doesn't contain headers for missing optional contacts`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    representativeWithContacts = null,
                    propertyDeveloperWithContacts = null,
                )

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työstä vastaava")
                contains("Työn suorittaja")
                doesNotContain("Rakennuttaja")
                doesNotContain("Asianhoitaja")
            }
        }

        @Test
        fun `created PDF contains contact information`() {
            val hakija =
                HakemusyhteystietoFactory.create(
                        nimi = "Company Ltd",
                        registryKey = "1054713-0",
                        sahkoposti = "info@company.test",
                        puhelinnumero = "050123456789",
                    )
                    .withYhteyshenkilo(
                        etunimi = "Cole",
                        sukunimi = "Contact",
                        sahkoposti = "cole@company.test",
                        puhelin = "050987654321",
                    )
                    .withYhteyshenkilo(
                        etunimi = "Seth",
                        sukunimi = "Secondary",
                        sahkoposti = "seth@company.test",
                        puhelin = "0505556666",
                    )
            val tyonSuorittaja =
                HakemusyhteystietoFactory.create(
                        nimi = "Contractor Inc.",
                        registryKey = "0156555-6",
                        sahkoposti = "info@contractor.test",
                        puhelinnumero = "0509999999",
                    )
                    .withYhteyshenkilo(
                        etunimi = "Cody",
                        sukunimi = "Contractor",
                        sahkoposti = "cody@contractor.test",
                        puhelin = "0501111111",
                        tilaaja = true)
            val asianhoitaja =
                HakemusyhteystietoFactory.create(
                    nimi = "Reynold Representative",
                    registryKey = "281192-937W",
                    sahkoposti = "reynold@company.test",
                    puhelinnumero = "0509990000",
                )
            val rakennuttaja =
                HakemusyhteystietoFactory.create(
                        nimi = "Developer Inc.",
                        registryKey = "8545758-6",
                        sahkoposti = "info@developer.test",
                        puhelinnumero = "0508888888",
                    )
                    .withYhteyshenkilo(
                        etunimi = "Denise",
                        sukunimi = "Developer",
                        sahkoposti = "denise@developer.test",
                        puhelin = "0502222222",
                    )

            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    customerWithContacts = hakija,
                    contractorWithContacts = tyonSuorittaja,
                    representativeWithContacts = asianhoitaja,
                    propertyDeveloperWithContacts = rakennuttaja,
                )

            val pdfData = KaivuilmoitusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Company Ltd")
                contains("1054713-0")
                contains("info@company.test")
                contains("050123456789")
                contains("Cole Contact")
                contains("cole@company.test")
                contains("050987654321")
                contains("Seth Secondary")
                contains("seth@company.test")
                contains("0505556666")
                contains("Contractor Inc.")
                contains("0156555-6")
                contains("info@contractor.test")
                contains("0509999999")
                contains("Cody Contractor")
                contains("cody@contractor.test")
                contains("0501111111")
                contains("Reynold Representative")
                contains("281192-937W")
                contains("reynold@company.test")
                contains("0509990000")
                contains("Developer Inc.")
                contains("8545758-6")
                contains("info@developer.test")
                contains("0508888888")
                contains("Denise Developer")
                contains("denise@developer.test")
                contains("0502222222")
            }
        }

        @Test
        fun `created PDF contains attachment information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                )
            val attachments =
                listOf(
                    ApplicationAttachmentFactory.create(fileName = "first.pdf"),
                    ApplicationAttachmentFactory.create(fileName = "second.png"),
                    ApplicationAttachmentFactory.create(fileName = "third.gt"),
                    ApplicationAttachmentFactory.create(
                        fileName = "valtakirja.pdf",
                        attachmentType = ApplicationAttachmentType.VALTAKIRJA),
                    ApplicationAttachmentFactory.create(
                        fileName = "liikenne.pdf",
                        attachmentType = ApplicationAttachmentType.LIIKENNEJARJESTELY),
                )

            val pdfData =
                KaivuilmoitusPdfEncoder.createPdf(hakemusData, 614f, attachments, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("first.pdf")
                contains("second.png")
                contains("third.gt")
                contains("valtakirja.pdf")
                contains("liikenne.pdf")
            }
        }
    }

    private fun getPdfAsText(pdfData: ByteArray): String {
        val reader = PdfReader(pdfData)
        val pages = reader.numberOfPages
        val textExtractor = PdfTextExtractor(reader)
        return (1..pages).joinToString("\n") { textExtractor.getTextFromPage(it) }
    }

    private fun phraseAsRegexWithEscapes(phrase: String): Regex =
        phrase.splitToSequence(' ').map { "\\Q$it\\E" }.joinToString("\\s+").toRegex()

    private fun Assert<String>.hasPhrase(phrase: String) =
        containsMatch(phraseAsRegexWithEscapes(phrase))
}
