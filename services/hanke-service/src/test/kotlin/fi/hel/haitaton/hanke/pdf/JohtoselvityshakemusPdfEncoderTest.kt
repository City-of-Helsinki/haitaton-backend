package fi.hel.haitaton.hanke.pdf

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.PaperDecisionReceiverFactory
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JohtoselvityshakemusPdfEncoderTest {

    @Nested
    inner class CreatePdf {
        @Test
        fun `created PDF contains title and section headers`() {
            val hakemusData = HakemusFactory.createJohtoselvityshakemusData()

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData))
                .contains(
                    "Johtoselvityshakemus", "Perustiedot", "Alueet", "Yhteystiedot", "Liitteet")
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val hakemusData = HakemusFactory.createJohtoselvityshakemusData()

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

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
                    postalAddress = ApplicationFactory.createPostalAddress(),
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    constructionWork = true,
                    maintenanceWork = true,
                    emergencyWork = true,
                    propertyConnectivity = true,
                )

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(applicationData, 1f, listOf(), listOf())

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
                    "${HakemusyhteyshenkiloFactory.DEFAULT_ETUNIMI} ${HakemusyhteyshenkiloFactory.DEFAULT_SUKUNIMI}")
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

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

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

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

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
                            JohtoselvitysHakemusalue("Ensimmäinen työalue", Polygon()),
                            JohtoselvitysHakemusalue("Toinen alue", Polygon()),
                            JohtoselvitysHakemusalue("", Polygon()),
                        ))

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(
                    hakemusData, 614f, listOf(185f, 231f, 198f), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("18.11.2022")
                contains("28.11.2022")
                contains("614,00 m²")
                contains("Ensimmäinen työalue")
                contains("185,00 m²")
                contains("Toinen alue")
                contains("231,00 m²")
                contains("Työalue 3")
                contains("198,00 m²")
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
                        HakemusyhteystietoFactory.create().withYhteyshenkilo())

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 1f, listOf(), listOf())

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
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    representativeWithContacts = null,
                    propertyDeveloperWithContacts = null,
                )

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työstä vastaava")
                contains("Työn suorittaja")
                doesNotContain("Rakennuttaja")
                doesNotContain("Asianhoitaja")
            }
        }

        @Test
        fun `created PDF contains contact information`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = createCompany(),
                    contractorWithContacts = createContractor(),
                    representativeWithContacts = createRepresentative(),
                    propertyDeveloperWithContacts = createDeveloper(),
                )

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

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
        fun `created PDF contains paper decision receiver when present on the application`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    paperDecisionReceiver = PaperDecisionReceiverFactory.default)

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).all {
                contains("Päätös tilattu paperisena")
                contains("Pekka Paperinen")
                contains("Paperipolku 3 A 4")
                contains("00451 Helsinki")
            }
        }

        @Test
        fun `created PDF doesn't contain paper decision receiver header when not present on the application`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(paperDecisionReceiver = null)

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), listOf())

            assertThat(getPdfAsText(pdfData)).doesNotContain("Päätös tilattu paperisena")
        }

        @Test
        fun `created PDF contains attachment information`() {
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                    areas = listOf())
            val attachments =
                listOf(
                    ApplicationAttachmentFactory.create(fileName = "first.pdf"),
                    ApplicationAttachmentFactory.create(fileName = "second.png"),
                    ApplicationAttachmentFactory.create(fileName = "third.gt"),
                )

            val pdfData =
                JohtoselvityshakemusPdfEncoder.createPdf(hakemusData, 614f, listOf(), attachments)

            assertThat(getPdfAsText(pdfData)).all {
                contains("first.pdf")
                contains("second.png")
                contains("third.gt")
            }
        }
    }

    private fun getPdfAsText(pdfData: ByteArray): String {
        val reader = PdfReader(pdfData)
        val pages = reader.numberOfPages
        val textExtractor = PdfTextExtractor(reader)
        return (1..pages).joinToString("\n") { textExtractor.getTextFromPage(it) }
    }

    companion object {
        fun createCompany() =
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

        fun createContractor() =
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
                    tilaaja = true,
                )

        fun createRepresentative() =
            HakemusyhteystietoFactory.create(
                nimi = "Reynold Representative",
                registryKey = "281192-937W",
                sahkoposti = "reynold@company.test",
                puhelinnumero = "0509990000",
            )

        fun createDeveloper() =
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
    }
}
