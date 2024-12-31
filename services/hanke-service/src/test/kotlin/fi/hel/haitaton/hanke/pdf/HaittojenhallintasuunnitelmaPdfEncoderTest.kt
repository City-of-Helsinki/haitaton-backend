package fi.hel.haitaton.hanke.pdf

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.Path
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HaittojenhallintasuunnitelmaPdfEncoderTest {

    @Nested
    inner class CreatePdf {
        val hanke = HankeFactory.create()

        @Test
        fun `created PDF contains title and section headers`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData()

            val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData))
                .contains(
                    "Hämeentien perusparannus ja katuvalot",
                    "Haittojenhallintasuunnitelma",
                    "Hakemuksen oletusnimi",
                    "Perustiedot",
                    "Alueet",
                )
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData(cableReportDone = false)

            val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työn nimi")
                contains("Työn kuvaus")
                contains("Työssä on kyse")
                hasPhrase("Tehtyjen johtoselvitysten tunnukset")
                contains("Uusi johtoselvitys")
                contains("Sijoitussopimustunnukset")
                contains("Työhön vaadittavat pätevyydet")
            }
        }

        @Test
        fun `created PDF contains basic information`() {
            val hakemusData =
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
                            ),
                )

            val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            val pdfText = getPdfAsText(pdfData)
            assertThat(pdfText).all {
                contains(ApplicationFactory.DEFAULT_APPLICATION_NAME)
                contains(ApplicationFactory.DEFAULT_WORK_DESCRIPTION)
                contains("Uuden rakenteen tai johdon rakentamisesta")
                contains("Olemassaolevan rakenteen kunnossapitotyöstä")
                hasPhrase(
                    "Kaivutyö on aloitettu ennen kaivuilmoituksen tekemistä " +
                        "merkittävien vahinkojen välttämiseksi"
                )
                contains("JS2400001, JS2400002")
                hasPhrase("Louhitaanko työn yhteydessä, esimerkiksi kallioperää?: Ei")
                contains("Sopimus1, Sopimus2")
                contains("Kyllä")
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

            val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData)).all {
                doesNotContain("Uuden rakenteen tai johdon rakentamisesta")
                doesNotContain("Olemassaolevan rakenteen kunnossapitotyöstä")
                doesNotContain("välttämiseksi")
            }
        }

        @Test
        fun `created PDF contains headers for area information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData()

            val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("Alueiden kokonaispinta-ala")
                contains("Työn alkupäivämäärä")
                contains("Työn loppupäivämäärä")
            }
        }

        @Test
        fun `created PDF contains area information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                    areas = listOf(),
                )

            val pdfData = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("614,00 m²")
                contains("18.11.2022")
                contains("28.11.2022")
            }
        }
    }

    /**
     * Writes a new Haittojenhallintasuunnitelma PDF to the local filesystem for manual inspection.
     *
     * This test should be removed when the HAI-375 story is completed.
     */
    @Test
    fun `write to file to manually inspect content`() {
        val data =
            KaivuilmoitusData(
                applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                name = "Lorem Ipsum",
                workDescription =
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum",
                constructionWork = false,
                maintenanceWork = false,
                emergencyWork = true,
                cableReportDone = false,
                rockExcavation = true,
                cableReports = listOf("JS-2313241", "JS-21398"),
                placementContracts = listOf("SL1029392"),
                requiredCompetence = true,
                startTime =
                    ZonedDateTime.of(2021, 1, 1, 12, 12, 12, 0, ZoneId.of("Europe/Helsinki")),
                endTime = ZonedDateTime.of(2022, 1, 1, 12, 12, 12, 0, ZoneId.of("Europe/Helsinki")),
                areas = listOf(),
                paperDecisionReceiver = null,
                customerWithContacts = null,
                contractorWithContacts = null,
                propertyDeveloperWithContacts = null,
                representativeWithContacts = null,
                invoicingCustomer = null,
                additionalInfo = null,
            )

        val hakemus =
            Hakemus(
                id = 0,
                alluid = null,
                alluStatus = null,
                applicationIdentifier = null,
                applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                applicationData = data,
                hankeTunnus = "HAI-21389",
                hankeId = 0,
                valmistumisilmoitukset = mapOf(),
            )

        val hanke =
            Hanke(
                id = 0,
                hankeTunnus = hakemus.hankeTunnus,
                onYKTHanke = null,
                nimi = "Hankkeen nimi",
                kuvaus = null,
                vaihe = null,
                version = null,
                createdBy = null,
                createdAt = null,
                modifiedBy = null,
                modifiedAt = null,
                status = null,
                generated = false,
            )

        val bytes = HaittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, data, 500f)
        Files.write(Path("hhs-pdf-test.pdf"), bytes)
    }
}
