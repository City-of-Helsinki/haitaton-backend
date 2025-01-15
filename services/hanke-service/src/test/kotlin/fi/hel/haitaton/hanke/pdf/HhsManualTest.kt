package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import java.net.URI
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.io.path.Path
import org.geotools.http.HTTPClientFinder
import org.geotools.http.LoggingHTTPClient
import org.geotools.ows.wms.WebMapServer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Writes some new Haittojenhallintasuunnitelma PDFs to the local filesystem for manual inspection.
 *
 * This test should be removed when the HAI-375 story is completed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled
class HhsManualTest {

    private val capabilityUrl =
        "https://kartta.hel.fi/ws/geoserver/avoindata/wms?REQUEST=GetCapabilities&SERVICE=WMS"

    private val httpClient =
        LoggingHTTPClient(HTTPClientFinder.createClient()).apply { isTryGzip = true }
    private val uri = URI(capabilityUrl).toURL()
    private val mapGenerator = MapGenerator(WebMapServer(uri, httpClient))
    private val haittojenhallintasuunnitelmaPdfEncoder =
        HaittojenhallintasuunnitelmaPdfEncoder(mapGenerator)

    private val hankealueet: List<SavedHankealue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/hankealueet.json".asJsonResource()

    private val pystysuora: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/tall-area.json".asJsonResource()

    private val vaakasuora: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/wide-area.json".asJsonResource()

    private val pieniAlue: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/small-area.json".asJsonResource()

    private val montaAluetta: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/many-areas.json".asJsonResource()

    private val montaHankealuetta: List<KaivuilmoitusAlue> =
        "/fi/hel/haitaton/hanke/pdf-test-data/many-hankealue.json".asJsonResource()

    private fun alueet(): List<Arguments> =
        listOf(
            Arguments.of("pystysuora", pystysuora),
            Arguments.of("vaakasuora", vaakasuora),
            Arguments.of("pieniAlue", pieniAlue),
            Arguments.of("montaAluetta", montaAluetta),
            Arguments.of("montaHankealuetta", montaHankealuetta),
        )

    @ParameterizedTest(name = "{displayName} {0}")
    @MethodSource("alueet")
    fun `write to file to manually inspect content`(name: String, areas: List<KaivuilmoitusAlue>) {
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
                areas = areas,
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

        val hankealueet =
            hankealueet.filter { hankealue -> areas.any { it.hankealueId == hankealue.id } }
        hanke.alueet = hankealueet.toMutableList()

        val bytes = haittojenhallintasuunnitelmaPdfEncoder.createPdf(hanke, data, 500f)
        Files.write(Path("hhs-pdf-test-$name.pdf"), bytes)
    }
}
