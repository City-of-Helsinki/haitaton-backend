package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import fi.hel.haitaton.hanke.application.CableReportApplicationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createPostalAddress
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HakemusPdfServiceTest {

    @Nested
    inner class CreatePdf {
        @Test
        fun `created PDF contains title and section headers`() {
            val hakemusData = HakemusFactory.createJohtoselvityshakemusData()

            val pdfData = HakemusPdfService.createPdf(hakemusData, 1f, listOf())

            assertThat(getPdfAsText(pdfData))
                .contains("Johtoselvityshakemus", "Perustiedot", "Alueet", "Yhteystiedot")
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val hakemusData = HakemusFactory.createJohtoselvityshakemusData()

            val pdfData = HakemusPdfService.createPdf(hakemusData, 614f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työn nimi")
                contains("Osoitetiedot")
                contains("Työssä on kyse")
                contains("Työn kuvaus")
                contains("Omat tiedot")
            }
        }

        @Test
        fun `created PDF contains basic information`() {
            val applicationData =
                HakemusFactory.createJohtoselvityshakemusData(
                    postalAddress = createPostalAddress(),
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    constructionWork = true,
                    maintenanceWork = true,
                    emergencyWork = true,
                    propertyConnectivity = true,
                )

            val pdfData = HakemusPdfService.createPdf(applicationData, 1f, listOf())

            val pdfText = getPdfAsText(pdfData)
            assertThat(pdfText).all {
                contains(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                contains("Katu 1")
                contains("00100")
                contains("Helsinki")
                contains("Uuden rakenteen tai johdon rakentamisesta")
                contains("Olemassaolevan rakenteen kunnossapitotyöstä")
                // This is too long to fit on one line in the PDF. It's not found as a continuous
                // String. Check for each word instead.
                contains(
                    "Kaivutyö",
                    "on",
                    "aloitettu",
                    "ennen",
                    "johtoselvityksen",
                    "tilaamista",
                    "merkittävien",
                    "vahinkojen",
                    "välttämiseksi",
                )
                contains("Kiinteistöliittymien rakentamisesta")
                contains(ApplicationFactory.DEFAULT_WORK_DESCRIPTION)
                contains(
                    "${HakemusyhteyshenkiloFactory.DEFAULT_ETUNIMI} ${HakemusyhteyshenkiloFactory.DEFAULT_SUKUNIMI}"
                )
                contains(HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI)
                contains(HakemusyhteyshenkiloFactory.DEFAULT_PUHELIN)
            }
        }

        @Test
        fun `created PDF doesn't have this work involves fields if none are selected`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    constructionWork = false,
                    maintenanceWork = false,
                    emergencyWork = false,
                    propertyConnectivity = false,
                )

            val pdfData = HakemusPdfService.createPdf(hakemusData, 1f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                doesNotContain("Uuden rakenteen tai johdon rakentamisesta")
                doesNotContain("Olemassaolevan rakenteen kunnossapitotyöstä")
                doesNotContain("välttämiseksi")
                doesNotContain("Kiinteistöliittymien rakentamisesta")
            }
        }

        @Test
        fun `created PDF contains headers for area information`() {
            val hakemusData = HakemusFactory.createJohtoselvityshakemusData()

            val pdfData = HakemusPdfService.createPdf(hakemusData, 614f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työn arvioitu alkupäivä")
                contains("Työn arvioitu loppupäivä")
                contains("Alueiden kokonaispinta-ala")
                contains("Alueet")
            }
        }

        @Test
        fun `created PDF contains area information`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                    areas =
                        listOf(
                            CableReportApplicationArea("Ensimmäinen työalue", Polygon()),
                            CableReportApplicationArea("Toinen alue", Polygon()),
                            CableReportApplicationArea("", Polygon()),
                        )
                )

            val pdfData = HakemusPdfService.createPdf(hakemusData, 614f, listOf(185f, 231f, 198f))

            assertThat(getPdfAsText(pdfData)).all {
                contains("18.11.2022")
                contains("28.11.2022")
                contains("614.0 m²")
                contains("Ensimmäinen työalue")
                contains("185.0 m²")
                contains("Toinen alue")
                contains("231.0 m²")
                contains("Työalue 3")
                contains("198.0 m²")
            }
        }

        @Test
        fun `contains headers for contact information`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    representativeWithContacts =
                        HakemusyhteystietoFactory.createPerson().withYhteyshenkilo(),
                    propertyDeveloperWithContacts =
                        HakemusyhteystietoFactory.create().withYhteyshenkilo()
                )

            val pdfData = HakemusPdfService.createPdf(hakemusData, 1f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työstä vastaavat")
                contains("Työn suorittajat")
                contains("Rakennuttajat")
                contains("Asianhoitajat")
                contains("Yhteyshenkilöt")
            }
        }

        @Test
        fun `doesn't contain headers for missing optional contacts`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    representativeWithContacts = null,
                    propertyDeveloperWithContacts = null,
                )

            val pdfData = HakemusPdfService.createPdf(hakemusData, 614f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työstä vastaavat")
                contains("Työn suorittajat")
                doesNotContain("Rakennuttajat")
                doesNotContain("Asianhoitajat")
            }
        }

        @Test
        fun `created PDF contains contact information`() {
            val hakija =
                HakemusyhteystietoFactory.create(
                        nimi = "Company Ltd",
                        ytunnus = "1054713-0",
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
                        ytunnus = "0156555-6",
                        sahkoposti = "info@contractor.test",
                        puhelinnumero = "0509999999",
                    )
                    .withYhteyshenkilo(
                        etunimi = "Cody",
                        sukunimi = "Contractor",
                        sahkoposti = "cody@contractor.test",
                        puhelin = "0501111111",
                        tilaaja = true
                    )
            val asianhoitaja =
                HakemusyhteystietoFactory.create(
                    nimi = "Reynold Representative",
                    ytunnus = "281192-937W",
                    sahkoposti = "reynold@company.test",
                    puhelinnumero = "0509990000",
                )
            val rakennuttaja =
                HakemusyhteystietoFactory.create(
                        nimi = "Developer Inc.",
                        ytunnus = "8545758-6",
                        sahkoposti = "info@developer.test",
                        puhelinnumero = "0508888888",
                    )
                    .withYhteyshenkilo(
                        etunimi = "Denise",
                        sukunimi = "Developer",
                        sahkoposti = "denise@developer.test",
                        puhelin = "0502222222"
                    )

            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = hakija,
                    contractorWithContacts = tyonSuorittaja,
                    representativeWithContacts = asianhoitaja,
                    propertyDeveloperWithContacts = rakennuttaja,
                )

            val pdfData = HakemusPdfService.createPdf(hakemusData, 614f, listOf())

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
    }

    private fun getPdfAsText(pdfData: ByteArray): String {
        val reader = PdfReader(pdfData)
        val pages = reader.numberOfPages
        val textExtractor = PdfTextExtractor(reader)
        return (1..pages).joinToString("\n") { textExtractor.getTextFromPage(it) }
    }
}
