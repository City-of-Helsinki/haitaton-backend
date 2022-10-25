package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.mutate
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.PermissionFactory
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionProfiles
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import org.geojson.FeatureCollection
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Testing the Hanke Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */
@WebMvcTest(HankeController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
class HankeControllerITests(@Autowired val mockMvc: MockMvc) {

    private val mockedHankeTunnus = "HAI21-1"

    @Autowired lateinit var hankeService: HankeService // faking these calls
    @Autowired lateinit var permissionService: PermissionService
    @Autowired lateinit var hankeGeometriatService: HankeGeometriatService
    @Autowired lateinit var disclosureLogService: DisclosureLogService

    @AfterEach
    fun cleanup() {
        clearAllMocks()
    }

    @Test
    fun whenUserHasNoViewPermissionReturnNotFound() {
        // faking the service call
        every { hankeService.loadHanke(mockedHankeTunnus) }.returns(HankeFactory.create())
        every { permissionService.getPermissionByHankeIdAndUserId(any(), any()) }.returns(null)

        mockMvc
            .perform(get("/hankkeet/$mockedHankeTunnus").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound)

        verify { disclosureLogService wasNot Called }
    }

    @Test
    fun `When hankeTunnus is given then return Hanke with it (GET)`() {
        val hankeId = 123
        val userId = "test"
        val permission =
            Permission(55, userId, hankeId, listOf(PermissionCode.VIEW, PermissionCode.VIEW))
        val hanke = HankeFactory.create(hankeId, userId)
        // faking the service call
        every { hankeService.loadHanke(mockedHankeTunnus) }.returns(hanke)
        every { permissionService.getPermissionByHankeIdAndUserId(hankeId, userId) }
            .returns(permission)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(hanke, "test") }

        mockMvc
            .perform(get("/hankkeet/$mockedHankeTunnus").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nimi").value("Hämeentien perusparannus ja katuvalot"))

        verify { hankeService.loadHanke(mockedHankeTunnus) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(hanke, "test") }
    }

    @Test
    fun `When calling get without parameters then return all Hanke data without geometry`() {
        // because test call has limitation and automatically creates object for call, we need to
        // create "empty" object for init and verify
        val hankeIds = listOf(123, 444)
        val permissions =
            listOf(
                Permission(1, "test", 123, PermissionProfiles.HANKE_OWNER_PERMISSIONS),
                Permission(44, "test", 444, listOf(PermissionCode.VIEW))
            )
        val hankkeet =
            listOf(
                HankeFactory.create(
                    id = 123,
                    alkuPvm = DateFactory.getStartDatetime().minusDays(500),
                    loppuPvm = DateFactory.getEndDatetime().minusDays(450),
                ),
                HankeFactory.create(
                    id = 444,
                    hankeTunnus = "hanketunnus2",
                    nimi = "Esplanadin viemäröinti",
                )
            )
        // faking the service call with two returned Hanke
        every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
        every { permissionService.getPermissionsByUserId("test") }.returns(permissions)
        justRun { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, "test") }

        // we check that we get the two hankeTunnus we expect
        mockMvc
            .perform(get("/hankkeet").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[2]").doesNotExist())
            .andExpect(jsonPath("$[0].hankeTunnus").value(mockedHankeTunnus))
            .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
            .andExpect(jsonPath("$[0].id").value(123))
            .andExpect(jsonPath("$[1].id").value(444))
            .andExpect(jsonPath("$[0].geometriat").doesNotExist())
            .andExpect(jsonPath("$[1].geometriat").doesNotExist())
            .andExpect(jsonPath("$[0].permissions").isArray)
            .andExpect(
                jsonPath("$[0].permissions")
                    .value(hasSize<Array<Any>>(PermissionProfiles.HANKE_OWNER_PERMISSIONS.size))
            )
            .andExpect(jsonPath("$[1].permissions").isArray)
            .andExpect(jsonPath("$[1].permissions").value(hasSize<Array<Any>>(1)))

        verify { hankeService.loadHankkeetByIds(hankeIds) }
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, "test") }
    }

    @Test
    fun `When calling get with geometry=true then return all Hanke data with geometry`() {
        // faking the service call with two returned Hanke
        val hankeIds = listOf(123, 444)
        val permissions =
            listOf(
                Permission(1, "test", 123, PermissionProfiles.HANKE_OWNER_PERMISSIONS),
                Permission(2, "test", 444, listOf(PermissionCode.VIEW))
            )
        val hankkeet = listOf(Hanke(123, mockedHankeTunnus), Hanke(444, "hanketunnus2"))
        every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
        every { hankeGeometriatService.loadGeometriat(Hanke(123, mockedHankeTunnus)) }
            .returns(HankeGeometriat(1, 123, FeatureCollection()))
        every { hankeGeometriatService.loadGeometriat(Hanke(444, "hanketunnus2")) }
            .returns(HankeGeometriat(2, 444, FeatureCollection()))
        every { permissionService.getPermissionsByUserId("test") }.returns(permissions)
        justRun { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, "test") }

        // we check that we get the two hankeTunnus and geometriat we expect
        mockMvc
            .perform(get("/hankkeet?geometry=true").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].hankeTunnus").value(mockedHankeTunnus))
            .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
            .andExpect(jsonPath("$[0].id").value(123))
            .andExpect(jsonPath("$[1].id").value(444))
            .andExpect(jsonPath("$[0].geometriat.id").value(1))
            .andExpect(jsonPath("$[1].geometriat.id").value(2))
            .andExpect(jsonPath("$[0].permissions").isArray)
            .andExpect(
                jsonPath("$[0].permissions")
                    .value(hasSize<Array<Any>>(PermissionProfiles.HANKE_OWNER_PERMISSIONS.size))
            )
            .andExpect(jsonPath("$[1].permissions").isArray)
            .andExpect(jsonPath("$[1].permissions").value(hasSize<Array<Any>>(1)))

        verify { hankeService.loadHankkeetByIds(hankeIds) }
        verify { hankeGeometriatService.loadGeometriat(Hanke(123, mockedHankeTunnus)) }
        verify { hankeGeometriatService.loadGeometriat(Hanke(444, "hanketunnus2")) }
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, "test") }
    }

    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeToBeMocked =
            HankeFactory.create(
                id = null,
                hankeTunnus = null,
                version = null,
                createdAt = null,
                createdBy = null
            )
        val permission = PermissionFactory.create()
        val createdHanke =
            hankeToBeMocked.copy(
                id = 1,
                hankeTunnus = mockedHankeTunnus,
                createdBy = "test",
                createdAt = getCurrentTimeUTC()
            )

        // faking the service call
        every { hankeService.createHanke(any()) }.returns(createdHanke)
        every {
                permissionService.setPermission(
                    any(),
                    any(),
                    PermissionProfiles.HANKE_OWNER_PERMISSIONS
                )
            }
            .returns(permission)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(createdHanke, "test") }

        mockMvc
            .perform(
                post("/hankkeet")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(hankeToBeMocked.toJsonString())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.hankeTunnus").value(mockedHankeTunnus))
            .andExpect(content().json(createdHanke.toJsonString()))
        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(createdHanke, "test") }
    }

    @Test
    fun `Update Hanke with data and return it (PUT)`() {
        // initializing only part of the data for Hanke
        val hankeToBeUpdated = HankeFactory.create(version = null)
        val updatedHanke =
            hankeToBeUpdated.apply {
                modifiedAt = getCurrentTimeUTC()
                modifiedBy = "test"
            }

        // faking the service call
        every { hankeService.updateHanke(any()) }.returns(updatedHanke)
        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, "test") }

        mockMvc
            .perform(
                put("/hankkeet/${HankeFactory.defaultHankeTunnus}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(hankeToBeUpdated.toJsonString())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nimi").value(HankeFactory.defaultNimi))

        verify { hankeService.updateHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, "test") }
    }

    @Test
    fun `Add Hanke and HankeYhteystiedot and return it with newly created hankeTunnus (POST)`() {
        val hankeToBeMocked =
            HankeFactory.create(hankeTunnus = null, version = null, createdBy = "test")
                .withGeneratedOmistaja(1)

        val content = hankeToBeMocked.toJsonString()

        // changing some return values
        val expectedHanke =
            hankeToBeMocked.apply {
                hankeTunnus = mockedHankeTunnus
                id = 12
                omistajat[0].id = 3
            }

        // faking the service calls
        every {
                permissionService.setPermission(
                    any(),
                    any(),
                    PermissionProfiles.HANKE_OWNER_PERMISSIONS
                )
            }
            .returns(PermissionFactory.create())
        every { hankeService.createHanke(any()) }.returns(expectedHanke)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, "test") }

        val expectedContent = expectedHanke.toJsonString()
        mockMvc
            .perform(
                post("/hankkeet")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(content)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            .andExpect(jsonPath("$.hankeTunnus").value(mockedHankeTunnus))
        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, "test") }
    }

    @Test
    fun `test tyomaa and haitat-fields roundtrip`() {
        // Initializing all fields
        val hankeToBeUpdated = HankeFactory.create(hankeTunnus = "idHankkeelle123")
        hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
        hankeToBeUpdated.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hankeToBeUpdated.haittaAlkuPvm = DateFactory.getStartDatetime()
        hankeToBeUpdated.haittaLoppuPvm = DateFactory.getEndDatetime()
        hankeToBeUpdated.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        hankeToBeUpdated.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
        hankeToBeUpdated.meluHaitta = Haitta13.YKSI
        hankeToBeUpdated.polyHaitta = Haitta13.KAKSI
        hankeToBeUpdated.tarinaHaitta = Haitta13.KOLME

        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku =
            DateFactory.getStartDatetime().truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
        val expectedDateLoppu =
            DateFactory.getEndDatetime().truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z
        val expectedHanke =
            hankeToBeUpdated.apply {
                alkuPvm = expectedDateAlku
                loppuPvm = expectedDateLoppu
                haittaAlkuPvm = expectedDateAlku
                haittaLoppuPvm = expectedDateLoppu
                modifiedBy = "test"
                modifiedAt = getCurrentTimeUTC()
            }
        val expectedContent = expectedHanke.toJsonString()

        // faking the service call
        every { hankeService.updateHanke(any()) }.returns(expectedHanke)
        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, "test") }

        // Call it and check results
        mockMvc
            .perform(
                put("/hankkeet/idHankkeelle123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(hankeToBeUpdated.toJsonString())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            // These might be redundant, but at least it is clear what we're checking here:
            .andExpect(jsonPath("$.tyomaaKatuosoite").value("Testikatu 1"))
            .andExpect(
                jsonPath("$.kaistaHaitta")
                    .value(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI.name)
            ) // Note, here as string, not the enum.
        verify { hankeService.updateHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, "test") }
    }

    @Test
    fun `test dates and times do not change on a roundtrip, except for rounding to midnight`() {
        val datetimeAlku = DateFactory.getStartDatetime()
        val datetimeLoppu = DateFactory.getEndDatetime()

        val hankeToBeMocked =
            HankeFactory.create(
                null,
                hankeTunnus = null,
                alkuPvm = datetimeAlku,
                loppuPvm = datetimeLoppu,
                createdAt = null,
                createdBy = null,
            )
        hankeToBeMocked.haittaAlkuPvm = datetimeAlku
        hankeToBeMocked.haittaLoppuPvm = datetimeLoppu

        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = datetimeAlku.truncatedTo(ChronoUnit.DAYS)
        val expectedDateLoppu = datetimeLoppu.truncatedTo(ChronoUnit.DAYS)
        val expectedHanke =
            hankeToBeMocked
                .copy(
                    hankeTunnus = mockedHankeTunnus,
                    id = 12,
                    alkuPvm = expectedDateAlku,
                    loppuPvm = expectedDateLoppu,
                    createdBy = "test",
                    createdAt = getCurrentTimeUTC()
                )
                .mutate {
                    it.haittaAlkuPvm = expectedDateAlku
                    it.haittaLoppuPvm = expectedDateLoppu
                }
        val expectedContent = expectedHanke.toJsonString()
        // JSON string versions without quotes:
        var expectedDateAlkuJson = expectedDateAlku.toJsonString()
        var expectedDateLoppuJson = expectedDateLoppu.toJsonString()
        expectedDateAlkuJson = expectedDateAlkuJson.substring(1, expectedDateAlkuJson.length - 1)
        expectedDateLoppuJson = expectedDateLoppuJson.substring(1, expectedDateLoppuJson.length - 1)

        // faking the service call
        every { hankeService.createHanke(any()) }.returns(expectedHanke)
        every {
                permissionService.setPermission(
                    any(),
                    any(),
                    PermissionProfiles.HANKE_OWNER_PERMISSIONS
                )
            }
            .returns(PermissionFactory.create())
        justRun { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, "test") }

        // Call it and check results
        mockMvc
            .perform(
                post("/hankkeet")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(hankeToBeMocked.toJsonString())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            // These might be redundant, but at least it is clear what we're checking here:
            .andExpect(jsonPath("$.alkuPvm").value(expectedDateAlkuJson))
            .andExpect(jsonPath("$.loppuPvm").value(expectedDateLoppuJson))
            .andExpect(jsonPath("$.haittaAlkuPvm").value(expectedDateAlkuJson))
            .andExpect(jsonPath("$.haittaLoppuPvm").value(expectedDateLoppuJson))
        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, "test") }
    }

    @Test
    fun `exception in Hanke creation causes a 500 Internal Server Error response with specific HankeError`() {
        val hanke =
            Hanke(
                "Testihanke",
                ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, TZ_UTC),
                ZonedDateTime.of(2021, 12, 31, 0, 0, 0, 0, TZ_UTC),
                Vaihe.OHJELMOINTI,
                SaveType.AUTO
            )
        // faking the service call
        every { hankeService.createHanke(any()) } throws RuntimeException("Some error")

        // Call it and check results
        mockMvc
            .perform(
                post("/hankkeet")
                    .contentType(MediaType.APPLICATION_JSON)
                    .characterEncoding("UTF-8")
                    .content(hanke.toJsonString())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json("""{"errorCode": "HAI0002", "errorMessage": "Internal error"}""")
            )

        verify { hankeService.createHanke(any()) }
    }
}
