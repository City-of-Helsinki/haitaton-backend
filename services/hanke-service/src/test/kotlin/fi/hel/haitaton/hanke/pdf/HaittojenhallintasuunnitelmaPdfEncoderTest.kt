package fi.hel.haitaton.hanke.pdf

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
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
        every { mapGenerator.mapWithAreas(any(), any(), any(), any(), any()) } returns blankImage
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(mapGenerator)
    }

    @Nested
    inner class CreatePdf {
        private val hankealueId = 414
        private val hankealueNimi = "Nimi hankealueelle"
        private val hankealue = HankealueFactory.create(id = hankealueId, nimi = hankealueNimi)

        private val hankealueId2 = 5256
        private val hankealueNimi2 = "Nimi toiselle hankealueelle"
        private val hankealue2 = HankealueFactory.create(id = hankealueId2, nimi = hankealueNimi2)

        private val hankealueet = listOf(hankealue, hankealue2)
        private val hanke =
            HankeFactory.create().apply { alueet = mutableListOf(hankealue, hankealue2) }

        private val hakemusalue =
            ApplicationFactory.createExcavationNotificationArea(hankealueId = hankealueId)
        private val haittojenhallintasuunnitelma2: Haittojenhallintasuunnitelma =
            mapOf(
                Haittojenhallintatyyppi.YLEINEN to "Yleiset toimet toiselle alueelle",
                Haittojenhallintatyyppi.AUTOLIIKENNE to "Autoilun toimet toiselle alueelle",
                Haittojenhallintatyyppi.PYORALIIKENNE to "Pyöräilyn toimet toiselle alueelle",
            )
        private val hakemusalue2 =
            ApplicationFactory.createExcavationNotificationArea(
                hankealueId = hankealueId2,
                haittojenhallintasuunnitelma = haittojenhallintasuunnitelma2,
            )
        private val hakemusalueet = listOf(hakemusalue, hakemusalue2)

        @Test
        fun `created PDF contains title and section headers`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData(areas = listOf())

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 1f)

            assertThat(getPdfAsText(pdfData))
                .contains(
                    "Hämeentien perusparannus ja katuvalot",
                    "Haittojenhallintasuunnitelma",
                    "Hakemuksen oletusnimi",
                    "Perustiedot",
                    "Alueet",
                )
            verifySequence { mapGenerator.mapWithAreas(listOf(), listOf(), 2220, 800, any()) }
        }

        @Test
        fun `created PDF contains headers for basic information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(areas = listOf(), cableReportDone = false)

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
            verifySequence { mapGenerator.mapWithAreas(listOf(), listOf(), 2220, 800, any()) }
        }

        @Test
        fun `created PDF contains basic information`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    areas = listOf(),
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
            verifySequence { mapGenerator.mapWithAreas(listOf(), listOf(), 2220, 800, any()) }
        }

        @Test
        fun `created PDF doesn't have this work involves fields if none are selected`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    areas = listOf(),
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
            verifySequence { mapGenerator.mapWithAreas(listOf(), listOf(), 2220, 800, any()) }
        }

        @Test
        fun `created PDF contains headers for area information`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData(areas = listOf())

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData)).all {
                contains("Alueiden kokonaispinta-ala")
                contains("Työn alkupäivämäärä")
                contains("Työn loppupäivämäärä")
            }
            verifySequence { mapGenerator.mapWithAreas(listOf(), listOf(), 2220, 800, any()) }
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
            verifySequence { mapGenerator.mapWithAreas(listOf(), listOf(), 2220, 800, any()) }
        }

        @Test
        fun `contains headers for haittojenhallintasuunnitelma when hankealue matches with hakemusalue`() {
            val hakemusData = HakemusFactory.createKaivuilmoitusData(areas = hakemusalueet)

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData).replace("\\s+".toRegex(), " ")).all {
                contains("Toimet työalueiden haittojen hallintaan, $hankealueNimi")
                contains("Linja-autojen paikallisliikenne, $hankealueNimi")
                contains("Autoliikenteen ruuhkautuminen, $hankealueNimi")
                contains("Pyöräliikenteen merkittävyys, $hankealueNimi")
                contains("Raitioliikenne, $hankealueNimi")
                contains("Muut haittojenhallintatoimet, $hankealueNimi")
                contains("Toimet työalueiden haittojen hallintaan, $hankealueNimi2")
                contains("Linja-autojen paikallisliikenne, $hankealueNimi2")
                contains("Autoliikenteen ruuhkautuminen, $hankealueNimi2")
                contains("Pyöräliikenteen merkittävyys, $hankealueNimi2")
                contains("Raitioliikenne, $hankealueNimi2")
                contains("Muut haittojenhallintatoimet, $hankealueNimi2")
            }
            verifySequence {
                val hakemusalueet1 = listOf(hakemusalue)
                val hankealueet1 = listOf(hankealue)
                mapGenerator.mapWithAreas(hakemusalueet, hankealueet, 2220, 800, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                val hakemusalueet2 = listOf(hakemusalue2)
                val hankealueet2 = listOf(hankealue2)
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
            }
        }

        @Test
        fun `contains texts from haittojenhallintasuunnitelma when hankealue matches with hakemusalue`() {
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(areas = listOf(hakemusalue, hakemusalue2))

            val pdfData = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, hakemusData, 614f)

            assertThat(getPdfAsText(pdfData).replace("\\s+".toRegex(), " ")).all {
                contains(HaittaFactory.DEFAULT_HHS_YLEINEN)
                contains(HaittaFactory.DEFAULT_HHS_LINJAAUTOLIIKENNE)
                contains(HaittaFactory.DEFAULT_HHS_AUTOLIIKENNE)
                contains(HaittaFactory.DEFAULT_HHS_PYORALIIKENNE)
                contains(HaittaFactory.DEFAULT_HHS_RAITIOLIIKENNE)
                contains(HaittaFactory.DEFAULT_HHS_MUUT)
                contains(haittojenhallintasuunnitelma2[Haittojenhallintatyyppi.YLEINEN]!!)
                contains(haittojenhallintasuunnitelma2[Haittojenhallintatyyppi.AUTOLIIKENNE]!!)
                contains(haittojenhallintasuunnitelma2[Haittojenhallintatyyppi.PYORALIIKENNE]!!)
                contains("Haitaton ei ole tunnistanut hankealueelta tätä kohderyhmää")
            }
            verifySequence {
                val hakemusalueet1 = listOf(hakemusalue)
                val hankealueet1 = listOf(hankealue)
                mapGenerator.mapWithAreas(hakemusalueet, hankealueet, 2220, 800, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet1, hankealueet1, 1720, 608, any())
                val hakemusalueet2 = listOf(hakemusalue2)
                val hankealueet2 = listOf(hankealue2)
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
                mapGenerator.mapWithAreas(hakemusalueet2, hankealueet2, 1720, 608, any())
            }
        }
    }
}
