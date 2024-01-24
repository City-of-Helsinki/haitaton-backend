package fi.hel.haitaton.hanke.allu

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createPostalAddress
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ApplicationPdfServiceTest {

    @Nested
    inner class CreatePdf {
        @Test
        fun `created PDF contains title and section headers`() {
            val applicationData = ApplicationFactory.createCableReportApplicationData()

            val pdfData = ApplicationPdfService.createPdf(applicationData, 1f, listOf())

            assertThat(getPdfAsText(pdfData))
                .contains("Johtoselvityshakemus", "Perustiedot", "Alueet", "Yhteystiedot")
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val applicationData = ApplicationFactory.createCableReportApplicationData()

            val pdfData = ApplicationPdfService.createPdf(applicationData, 614f, listOf())

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
                ApplicationFactory.createCableReportApplicationData(
                        postalAddress = createPostalAddress(),
                        workDescription = "Työn kuvaus kirjoitetaan tähän.",
                        customerWithContacts =
                            ApplicationFactory.createCompanyCustomer()
                                .withContacts(
                                    ApplicationFactory.createContact(
                                        "Teppo Tilaaja",
                                        "teppo@tilaajat.info",
                                        "0406584321",
                                        orderer = true,
                                    )
                                )
                    )
                    .copy(
                        constructionWork = true,
                        maintenanceWork = true,
                        emergencyWork = true,
                        propertyConnectivity = true,
                    )

            val pdfData = ApplicationPdfService.createPdf(applicationData, 1f, listOf())

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
                contains("Työn kuvaus kirjoitetaan tähän.")
                contains("Teppo Tilaaja")
                contains("teppo@tilaajat.info")
                contains("0406584321")
            }
        }

        @Test
        fun `created PDF doesn't have this work involves fields if none are selected`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData()
                    .copy(
                        constructionWork = false,
                        maintenanceWork = false,
                        emergencyWork = false,
                        propertyConnectivity = false,
                    )

            val pdfData = ApplicationPdfService.createPdf(applicationData, 1f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                doesNotContain("Uuden rakenteen tai johdon rakentamisesta")
                doesNotContain("Olemassaolevan rakenteen kunnossapitotyöstä")
                doesNotContain("välttämiseksi")
                doesNotContain("Kiinteistöliittymien rakentamisesta")
            }
        }

        @Test
        fun `created PDF contains headers for area information`() {
            val applicationData = ApplicationFactory.createCableReportApplicationData()

            val pdfData = ApplicationPdfService.createPdf(applicationData, 614f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työn arvioitu alkupäivä")
                contains("Työn arvioitu loppupäivä")
                contains("Alueiden kokonaispinta-ala")
                contains("Alueet")
            }
        }

        @Test
        fun `created PDF contains area information`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                    areas =
                        listOf(
                            ApplicationArea("Ensimmäinen työalue", Polygon()),
                            ApplicationArea("Toinen alue", Polygon()),
                            ApplicationArea("", Polygon()),
                        )
                )

            val pdfData =
                ApplicationPdfService.createPdf(applicationData, 614f, listOf(185f, 231f, 198f))

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
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(ApplicationFactory.createContact()),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(
                                ApplicationFactory.createContact(),
                            ),
                    representativeWithContacts =
                        ApplicationFactory.createPersonCustomer().withContacts(),
                    propertyDeveloperWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(
                                ApplicationFactory.createContact(),
                            ),
                )

            val pdfData = ApplicationPdfService.createPdf(applicationData, 614f, listOf())

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
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(ApplicationFactory.createContact()),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer()
                            .withContacts(
                                ApplicationFactory.createContact(),
                            ),
                    representativeWithContacts = null,
                    propertyDeveloperWithContacts = null,
                )

            val pdfData = ApplicationPdfService.createPdf(applicationData, 614f, listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työstä vastaavat")
                contains("Työn suorittajat")
                doesNotContain("Rakennuttajat")
                doesNotContain("Asianhoitajat")
            }
        }

        @Test
        fun `created PDF contains contact information`() {
            val applicationData =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer(
                                name = "Company Ltd",
                                registryKey = "1054713-0",
                                email = "info@company.test",
                                phone = "050123456789",
                            )
                            .withContacts(
                                ApplicationFactory.createContact(
                                    firstName = "Cole Contact",
                                    lastName = "",
                                    email = "cole@company.test",
                                    phone = "050987654321",
                                ),
                                ApplicationFactory.createContact(
                                    firstName = "Seth",
                                    lastName = "Secondary",
                                    email = "seth@company.test",
                                    phone = "0505556666",
                                ),
                            ),
                    contractorWithContacts =
                        ApplicationFactory.createCompanyCustomer(
                                name = "Contractor Inc.",
                                registryKey = "0156555-6",
                                email = "info@contractor.test",
                                phone = "0509999999",
                            )
                            .withContacts(
                                ApplicationFactory.createContact(
                                    firstName = "Cody",
                                    lastName = "Contractor",
                                    email = "cody@contractor.test",
                                    phone = "0501111111",
                                    orderer = true
                                ),
                            ),
                    representativeWithContacts =
                        ApplicationFactory.createPersonCustomer(
                                name = "Reynold Representative",
                                registryKey = "281192-937W",
                                email = "reynold@company.test",
                                phone = "0509990000",
                            )
                            .withContacts(),
                    propertyDeveloperWithContacts =
                        ApplicationFactory.createCompanyCustomer(
                                name = "Developer Inc.",
                                registryKey = "8545758-6",
                                email = "info@developer.test",
                                phone = "0508888888",
                            )
                            .withContacts(
                                ApplicationFactory.createContact(
                                    firstName = "Denise",
                                    lastName = "Developer",
                                    email = "denise@developer.test",
                                    phone = "0502222222"
                                ),
                            ),
                )

            val pdfData = ApplicationPdfService.createPdf(applicationData, 614f, listOf())

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
