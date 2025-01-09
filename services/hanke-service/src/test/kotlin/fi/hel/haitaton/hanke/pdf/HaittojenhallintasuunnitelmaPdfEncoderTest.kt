package fi.hel.haitaton.hanke.pdf

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.getResourceAsBytes
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HaittojenhallintasuunnitelmaPdfEncoderTest {

    private val mapGenerator: MapGenerator = mockk()

    private val haittojenhallintasuunnitelmaPdfEncoder =
        HaittojenhallintasuunnitelmaPdfEncoder(mapGenerator)

    private val blankImage: ByteArray =
        "/fi/hel/haitaton/hanke/pdf-test-data/blank.png".getResourceAsBytes()

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
        every { mapGenerator.mapWithAreas(any(), any(), any(), any()) } returns blankImage
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(mapGenerator)
    }

    @Nested
    inner class CreatePdf {
        val hanke = HankeFactory.create()

        @Test
        fun `created PDF contains title and section headers`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData()

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData))
                .contains(
                    "Hämeentien perusparannus ja katuvalot",
                    "Haittojenhallintasuunnitelma",
                    "Hakemuksen oletusnimi",
                    "Perustiedot",
                    "Alueet",
                )
            verifySequence { mapGenerator.mapWithAreas(hakemusData.areas!!, listOf(), 2220, 800) }
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData(cableReportDone = false)

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("Työn nimi")
                contains("Työn kuvaus")
                contains("Työssä on kyse")
                hasPhrase("Tehtyjen johtoselvitysten tunnukset")
                contains("Uusi johtoselvitys")
                contains("Sijoitussopimustunnukset")
                contains("Työhön vaadittavat pätevyydet")
            }
            verifySequence { mapGenerator.mapWithAreas(hakemusData.areas!!, listOf(), 2220, 800) }
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

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

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
            verifySequence { mapGenerator.mapWithAreas(hakemusData.areas!!, listOf(), 2220, 800) }
        }

        @Test
        fun `created PDF doesn't have this work involves fields if none are selected`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    constructionWork = false,
                    maintenanceWork = false,
                    emergencyWork = false,
                )

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData)).all {
                doesNotContain("Uuden rakenteen tai johdon rakentamisesta")
                doesNotContain("Olemassaolevan rakenteen kunnossapitotyöstä")
                doesNotContain("välttämiseksi")
            }
            verifySequence { mapGenerator.mapWithAreas(hakemusData.areas!!, listOf(), 2220, 800) }
        }

        @Test
        fun `created PDF contains headers for area information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData()

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("Alueiden kokonaispinta-ala")
                contains("Työn alkupäivämäärä")
                contains("Työn loppupäivämäärä")
            }
            verifySequence { mapGenerator.mapWithAreas(hakemusData.areas!!, listOf(), 2220, 800) }
        }

        @Test
        fun `created PDF contains area information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    startTime = ZonedDateTime.parse("2022-11-17T22:00:00.000Z"),
                    endTime = ZonedDateTime.parse("2022-11-28T21:59:59.999Z"),
                    areas = listOf(),
                )

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("614,00 m²")
                contains("18.11.2022")
                contains("28.11.2022")
            }
            verifySequence { mapGenerator.mapWithAreas(hakemusData.areas!!, listOf(), 2220, 800) }
        }
    }
}
