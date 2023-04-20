package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationsResponse
import fi.hel.haitaton.hanke.domain.HankeWithApplications
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withPerustaja
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.liitteet.AttachmentMetadata
import fi.hel.haitaton.hanke.liitteet.AttachmentScanStatus
import fi.hel.haitaton.hanke.liitteet.AttachmentService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.geojson.FeatureCollection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val USERNAME = "test"
private const val HANKE_TUNNUS = "HAI21-1"
private const val BASE_URL = "/hankkeet"

/**
 * Testing the Hanke Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */
@WebMvcTest(HankeController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME, roles = ["haitaton-user"])
class HankeControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired lateinit var hankeService: HankeService // faking these calls
    @Autowired lateinit var permissionService: PermissionService
    @Autowired lateinit var attachmentService: AttachmentService
    @Autowired lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        confirmVerified(permissionService, disclosureLogService, hankeService)
    }

    @Test
    fun whenUserHasNoViewPermissionReturnNotFound() {
        every { hankeService.loadHanke(HANKE_TUNNUS) }.returns(HankeFactory.create())
        every { permissionService.hasPermission(any(), any(), PermissionCode.VIEW) }.returns(false)

        get("$BASE_URL/$HANKE_TUNNUS").andExpect(status().isNotFound)

        verify { disclosureLogService wasNot Called }
        verify { hankeService.loadHanke(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(any(), any(), PermissionCode.VIEW) }
    }

    @Test
    fun `When hankeTunnus is given then return Hanke with it (GET)`() {
        val hankeId = 123
        val hanke = HankeFactory.create(hankeId, USERNAME)
        every { hankeService.loadHanke(HANKE_TUNNUS) }.returns(hanke)
        every { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
            .returns(true)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }

        get("$BASE_URL/$HANKE_TUNNUS")
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nimi").value("Hämeentien perusparannus ja katuvalot"))
            .andExpect(jsonPath("$.status").value(HankeStatus.DRAFT.name))

        verify { hankeService.loadHanke(HANKE_TUNNUS) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(hanke, "test") }
        verify { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
    }

    @Test
    fun `When calling get without parameters then return all Hanke data without geometry`() {
        // because test call has limitation and automatically creates object for call, we need to
        // create "empty" object for init and verify
        val hankeIds = listOf(123, 444)
        val hankkeet =
            listOf(
                HankeFactory.create(
                    id = 123,
                ),
                HankeFactory.create(
                    id = 444,
                    hankeTunnus = "hanketunnus2",
                    nimi = "Esplanadin viemäröinti",
                )
            )
        // faking the service call with two returned Hanke
        every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
        every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
            .returns(hankeIds)
        justRun { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, USERNAME) }

        // we check that we get the two hankeTunnus we expect
        get(BASE_URL)
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[2]").doesNotExist())
            .andExpect(jsonPath("$[0].hankeTunnus").value(HANKE_TUNNUS))
            .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
            .andExpect(jsonPath("$[0].id").value(123))
            .andExpect(jsonPath("$[1].id").value(444))
            .andExpect(jsonPath("$[0].geometriat").doesNotExist())
            .andExpect(jsonPath("$[1].geometriat").doesNotExist())

        verify { hankeService.loadHankkeetByIds(hankeIds) }
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, "test") }
        verify { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
    }

    @Test
    fun `When calling get with geometry=true then return all Hanke data with geometry`() {
        // faking the service call with two returned Hanke
        val hankeIds = listOf(123, 444)
        val alue1 = Hankealue(hankeId = 123, geometriat = Geometriat(1, FeatureCollection()))
        val alue2 = Hankealue(hankeId = 444, geometriat = Geometriat(2, FeatureCollection()))
        val hanke1 = HankeFactory.create(id = 123, hankeTunnus = HANKE_TUNNUS)
        val hanke2 = HankeFactory.create(id = 444, hankeTunnus = "hanketunnus2")
        hanke1.alueet = mutableListOf(alue1)
        hanke2.alueet = mutableListOf(alue2)
        val hankkeet = listOf(hanke1, hanke2)

        every { hankeService.loadHankkeetByIds(hankeIds) }.returns(listOf(hanke1, hanke2))
        every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
            .returns(hankeIds)
        justRun { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, USERNAME) }

        // we check that we get the two hankeTunnus and geometriat we expect
        get("$BASE_URL?geometry=true")
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].hankeTunnus").value(HANKE_TUNNUS))
            .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
            .andExpect(jsonPath("$[0].id").value(123))
            .andExpect(jsonPath("$[1].id").value(444))
            .andExpect(jsonPath("$[0].alueet[0].geometriat.id").value(1))
            .andExpect(jsonPath("$[1].alueet[0].geometriat.id").value(2))

        verify { hankeService.loadHankkeetByIds(hankeIds) }
        verify { disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, USERNAME) }
        verify { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
    }

    @Test
    fun `getHankeWithApplications with unknown hanke tunnus returns 404`() {
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) } throws
            HankeNotFoundException(HANKE_TUNNUS)

        get("$BASE_URL/$HANKE_TUNNUS/hakemukset").andExpect(status().isNotFound)

        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
    }

    @Test
    fun `getHankeWithApplications user is does not have permission returns 404`() {
        val hanke = HankeFactory.create()
        val applications = mockApplications()
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) } returns
            HankeWithApplications(hanke, applications)
        every { permissionService.hasPermission(hanke.id!!, USERNAME, PermissionCode.VIEW) } returns
            false

        get("$BASE_URL/$HANKE_TUNNUS/hakemukset").andExpect(status().isNotFound)

        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(hanke.id!!, USERNAME, PermissionCode.VIEW) }
    }

    @Test
    fun `getHankeWithApplications with no applications returns empty list`() {
        val hanke = HankeFactory.create()
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) } returns
            HankeWithApplications(hanke, listOf())
        every { permissionService.hasPermission(hanke.id!!, USERNAME, PermissionCode.VIEW) } returns
            true
        justRun { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }

        val response: ApplicationsResponse =
            get("$BASE_URL/$HANKE_TUNNUS/hakemukset").andExpect(status().isOk).andReturnBody()

        assertTrue(response.applications.isEmpty())
        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(hanke.id!!, USERNAME, PermissionCode.VIEW) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }
    }

    @Test
    fun `getHankeWithApplications with known hanketunnus returns applications`() {
        val hanke = HankeFactory.create()
        val applications = mockApplications()
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) } returns
            HankeWithApplications(hanke, applications)
        every { permissionService.hasPermission(hanke.id!!, USERNAME, PermissionCode.VIEW) } returns
            true
        justRun { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }
        justRun { disclosureLogService.saveDisclosureLogsForApplications(applications, USERNAME) }

        val response: ApplicationsResponse =
            get("$BASE_URL/$HANKE_TUNNUS/hakemukset").andExpect(status().isOk).andReturnBody()

        assertTrue(response.applications.isNotEmpty())
        assertEquals(ApplicationsResponse(applications), response)
        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(hanke.id!!, USERNAME, PermissionCode.VIEW) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }
        verify { disclosureLogService.saveDisclosureLogsForApplications(applications, USERNAME) }
    }

    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeToBeMocked =
            HankeFactory.create(
                id = null,
                hankeTunnus = null,
                version = null,
                createdAt = null,
                createdBy = null,
            )
        val createdHanke =
            hankeToBeMocked.copy(
                id = 1,
                hankeTunnus = HANKE_TUNNUS,
                version = 0,
                createdBy = USERNAME,
                createdAt = getCurrentTimeUTC(),
            )
        every { hankeService.createHanke(any()) }.returns(createdHanke)
        justRun { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
        justRun { disclosureLogService.saveDisclosureLogsForHanke(createdHanke, USERNAME) }

        postRaw(BASE_URL, hankeToBeMocked.toJsonString())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
            .andExpect(content().json(createdHanke.toJsonString()))

        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(createdHanke, USERNAME) }
        verify { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
    }

    @Test
    fun `createHanke when perustaja is provided should return provided information`() {
        val hanke = HankeFactory.create().withPerustaja()
        every { hankeService.createHanke(any()) } returns hanke
        justRun { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
        justRun { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }

        post(BASE_URL, hanke)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.perustaja.nimi").value("Pertti Perustaja"))
            .andExpect(jsonPath("$.perustaja.email").value("foo@bar.com"))

        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), any()) }
        verify { permissionService.setPermission(any(), any(), any()) }
    }

    @Test
    fun `createHanke sanitizes hanke input returns 200`() {
        val hanke = HankeFactory.create().apply { generated = true }
        every { hankeService.createHanke(hanke.copy(id = null, generated = false)) } returns
            hanke.copy(generated = false)
        justRun { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
        justRun { disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME) }

        post(BASE_URL, hanke).andExpect(status().isOk)

        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(any(), any()) }
        verify { permissionService.setPermission(any(), any(), any()) }
    }

    @Test
    fun `create hanke with perustaja without sahkoposti returns 400`() {
        val hakemus = HankeFactory.create().withPerustaja()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(hakemus)
        (content.get("perustaja") as ObjectNode).remove("sahkoposti")

        post(BASE_URL, content.toJsonString()).andExpect(status().isBadRequest)
    }

    @Test
    fun `Update Hanke without permission returns 404`() {
        val hanketunnus = HankeFactory.defaultHankeTunnus
        // initializing only part of the data for Hanke
        val hankeToBeUpdated = HankeFactory.create(version = null)
        val updatedHanke =
            hankeToBeUpdated.apply {
                modifiedAt = getCurrentTimeUTC()
                modifiedBy = USERNAME
            }
        every { hankeService.loadHanke(hanketunnus) } returns HankeFactory.create()
        every { permissionService.hasPermission(updatedHanke.id!!, USERNAME, PermissionCode.EDIT) }
            .returns(false)

        putRaw("$BASE_URL/$hanketunnus", hankeToBeUpdated.toJsonString())
            .andExpect(status().isNotFound)

        verify { hankeService.loadHanke(hanketunnus) }
        verify { permissionService.hasPermission(updatedHanke.id!!, USERNAME, PermissionCode.EDIT) }
    }

    @Test
    fun `Update Hanke with data and return it (PUT)`() {
        val hanketunnus = HankeFactory.defaultHankeTunnus
        // initializing only part of the data for Hanke
        val hankeToBeUpdated = HankeFactory.create(version = null)
        val updatedHanke =
            hankeToBeUpdated.apply {
                modifiedAt = getCurrentTimeUTC()
                modifiedBy = USERNAME
                status = HankeStatus.PUBLIC
            }
        every { hankeService.loadHanke(hanketunnus) } returns HankeFactory.create()
        every { permissionService.hasPermission(updatedHanke.id!!, USERNAME, PermissionCode.EDIT) }
            .returns(true)
        every { hankeService.updateHanke(any()) }.returns(updatedHanke)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, USERNAME) }

        putRaw("$BASE_URL/$hanketunnus", hankeToBeUpdated.toJsonString())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nimi").value(HankeFactory.defaultNimi))
            .andExpect(jsonPath("$.status").value(HankeStatus.PUBLIC.name))

        verify { hankeService.loadHanke(hanketunnus) }
        verify { permissionService.hasPermission(updatedHanke.id!!, USERNAME, PermissionCode.EDIT) }
        verify { hankeService.updateHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, USERNAME) }
    }

    @Test
    fun `Add Hanke and HankeYhteystiedot and return it with newly created hankeTunnus (POST)`() {
        val hankeToBeMocked =
            HankeFactory.create(hankeTunnus = null, version = null, createdBy = USERNAME)
                .withGeneratedOmistaja(1)
        val content = hankeToBeMocked.toJsonString()
        // changing some return values
        val expectedHanke =
            hankeToBeMocked.apply {
                hankeTunnus = HANKE_TUNNUS
                id = 12
                omistajat[0].id = 3
            }
        justRun { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
        every { hankeService.createHanke(any()) }.returns(expectedHanke)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME) }
        val expectedContent = expectedHanke.toJsonString()

        postRaw(BASE_URL, content)
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))

        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME) }
        verify { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
    }

    @Test
    fun `test tyomaa and haitat-fields roundtrip`() {
        val hanketunnus = "idHankkeelle123"
        // Initializing all fields
        val hankeToBeUpdated = HankeFactory.create(hankeTunnus = hanketunnus)
        hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
        val alue = Hankealue()
        alue.haittaAlkuPvm = DateFactory.getStartDatetime()
        alue.haittaLoppuPvm = DateFactory.getEndDatetime()
        alue.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        alue.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
        alue.meluHaitta = Haitta13.YKSI
        alue.polyHaitta = Haitta13.KAKSI
        alue.tarinaHaitta = Haitta13.KOLME
        hankeToBeUpdated.alueet.add(alue)
        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku =
            DateFactory.getStartDatetime().truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
        val expectedDateLoppu =
            DateFactory.getEndDatetime().truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z
        val expectedHanke =
            hankeToBeUpdated.apply {
                modifiedBy = USERNAME
                modifiedAt = getCurrentTimeUTC()
            }
        expectedHanke.alueet[0].haittaAlkuPvm = expectedDateAlku
        expectedHanke.alueet[0].haittaLoppuPvm = expectedDateLoppu
        val expectedContent = expectedHanke.toJsonString()
        every { hankeService.loadHanke(hanketunnus) } returns hankeToBeUpdated
        every {
            permissionService.hasPermission(expectedHanke.id!!, USERNAME, PermissionCode.EDIT)
        } returns true
        every { hankeService.updateHanke(any()) } returns expectedHanke
        justRun { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME) }

        // Call it and check results
        putRaw("$BASE_URL/idHankkeelle123", hankeToBeUpdated.toJsonString())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            // These might be redundant, but at least it is clear what we're checking here:
            .andExpect(jsonPath("$.tyomaaKatuosoite").value("Testikatu 1"))
            .andExpect(
                jsonPath("$.alueet[0].kaistaHaitta")
                    .value(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI.name)
            ) // Note, here as string, not the enum.

        verify { hankeService.loadHanke(hanketunnus) }
        verify {
            permissionService.hasPermission(expectedHanke.id!!, USERNAME, PermissionCode.EDIT)
        }
        verify { hankeService.updateHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME) }
    }

    @Test
    fun `test dates and times do not change on a roundtrip, except for rounding to midnight`() {
        val datetimeAlku = DateFactory.getStartDatetime()
        val datetimeLoppu = DateFactory.getEndDatetime()
        val hankeToBeMocked =
            HankeFactory.create(
                null,
                hankeTunnus = null,
                createdBy = USERNAME,
                createdAt = getCurrentTimeUTC()
            )
        val alue = Hankealue()
        alue.haittaAlkuPvm = datetimeAlku
        alue.haittaLoppuPvm = datetimeLoppu
        hankeToBeMocked.alueet.add(alue)
        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = datetimeAlku.truncatedTo(ChronoUnit.DAYS)
        val expectedDateLoppu = datetimeLoppu.truncatedTo(ChronoUnit.DAYS)
        val expectedHanke =
            hankeToBeMocked.apply {
                hankeTunnus = HANKE_TUNNUS
                id = 12
            }
        expectedHanke.alueet[0].haittaAlkuPvm = expectedDateAlku
        expectedHanke.alueet[0].haittaLoppuPvm = expectedDateLoppu
        val expectedContent = expectedHanke.toJsonString()
        // JSON string versions without quotes:
        var expectedDateAlkuJson = expectedDateAlku.toJsonString()
        var expectedDateLoppuJson = expectedDateLoppu.toJsonString()
        expectedDateAlkuJson = expectedDateAlkuJson.substring(1, expectedDateAlkuJson.length - 1)
        expectedDateLoppuJson = expectedDateLoppuJson.substring(1, expectedDateLoppuJson.length - 1)
        // faking the service call
        every { hankeService.createHanke(any()) }.returns(expectedHanke)
        justRun { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
        justRun { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME) }

        // Call it and check results
        postRaw(BASE_URL, hankeToBeMocked.toJsonString())
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            // These might be redundant, but at least it is clear what we're checking here:
            .andExpect(jsonPath("$.alkuPvm").value(expectedDateAlkuJson))
            .andExpect(jsonPath("$.loppuPvm").value(expectedDateLoppuJson))
            .andExpect(jsonPath("$.alueet[0].haittaAlkuPvm").value(expectedDateAlkuJson))
            .andExpect(jsonPath("$.alueet[0].haittaLoppuPvm").value(expectedDateLoppuJson))

        verify { hankeService.createHanke(any()) }
        verify { disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME) }
        verify { permissionService.setPermission(any(), any(), Role.KAIKKI_OIKEUDET) }
    }

    @Test
    fun `exception in Hanke creation causes a 500 Internal Server Error response with specific HankeError`() {
        val hanke =
            HankeFactory.create(
                id = null,
                hankeTunnus = null,
                nimi = "Testihanke",
                vaihe = Vaihe.OHJELMOINTI,
            )
        every { hankeService.createHanke(any()) } throws RuntimeException("Some error")

        postRaw(BASE_URL, hanke.toJsonString())
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json("""{"errorCode": "HAI0002", "errorMessage": "Internal error"}""")
            )

        verify { hankeService.createHanke(any()) }
    }

    @Test
    fun `delete when user has permission and hanke exists should call delete returns no content`() {
        val mockHankeId = 56
        val hankeWithApplications =
            HankeWithApplications(HankeFactory.create(id = mockHankeId), listOf())
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) }.returns(hankeWithApplications)
        every { permissionService.hasPermission(mockHankeId, USERNAME, PermissionCode.DELETE) }
            .returns(true)
        justRun {
            hankeService.deleteHanke(
                hankeWithApplications.hanke,
                hankeWithApplications.applications,
                USERNAME
            )
        }

        delete("$BASE_URL/$HANKE_TUNNUS").andExpect(status().isNoContent)

        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(mockHankeId, USERNAME, PermissionCode.DELETE) }
        verify {
            hankeService.deleteHanke(
                hankeWithApplications.hanke,
                hankeWithApplications.applications,
                USERNAME
            )
        }
    }

    @Test
    fun `delete when user does not have permission should not call delete returns not found`() {
        val mockHankeId = 56
        val hankeWithApplications =
            HankeWithApplications(HankeFactory.create(id = mockHankeId), listOf())
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) }.returns(hankeWithApplications)
        every { permissionService.hasPermission(mockHankeId, USERNAME, PermissionCode.DELETE) }
            .returns(false)

        delete("$BASE_URL/$HANKE_TUNNUS").andExpect(status().isNotFound)

        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(mockHankeId, USERNAME, PermissionCode.DELETE) }
    }

    @Test
    fun `delete when hanke does not exist should not call delete returns not found`() {
        every { hankeService.getHankeWithApplications(HANKE_TUNNUS) } answers
            {
                throw HankeNotFoundException(HANKE_TUNNUS)
            }

        delete("$BASE_URL/$HANKE_TUNNUS").andExpect(status().isNotFound)

        verify { hankeService.getHankeWithApplications(HANKE_TUNNUS) }
    }

    private fun mockApplications(): List<Application> {
        return (1..5).map {
            AlluDataFactory.createApplication(it.toLong(), hankeTunnus = HANKE_TUNNUS)
        }
    }

    @Test
    fun `List of attachments can be loaded`() {
        val hankeTunnus = "HAI-123"
        val hankeId = 123
        val userId = currentUserId()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.VIEW) }
            .returns(true)
        every { attachmentService.getHankeAttachments(hankeTunnus) }
            .returns(
                listOf(
                    AttachmentFactory.create(hankeTunnus, name = "file1.pdf"),
                    AttachmentFactory.create(hankeTunnus, name = "file2.pdf"),
                    AttachmentFactory.create(hankeTunnus, name = "file3.pdf"),
                )
            )

        get("/hankkeet/${hankeTunnus}/liitteet").andExpect(status().`is`(200))

        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.VIEW) }
        verify { attachmentService.getHankeAttachments(hankeTunnus) }
    }

    @Test
    @WithAnonymousUser
    fun `Uploading without a session should fail`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        sendAttachment("HAI-123", file).andExpect(status().`is`(401))
    }

    @Test
    fun `Uploading with unknown hankeTunnus should fail`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        val hankeTunnus = "123"

        every { hankeService.getHankeId(hankeTunnus) }.returns(null)

        sendAttachment(hankeTunnus, file).andExpect(status().`is`(404))

        verify { hankeService.getHankeId(hankeTunnus) }
    }

    @Test
    fun `Upload should fail when no rights for hanke`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        val hankeTunnus = "HAI-123"
        val hankeId = 123
        val userId = currentUserId()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
            .returns(false)

        sendAttachment(hankeTunnus, file).andExpect(status().`is`(404))

        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
    }

    @Test
    fun `Uploading a file with user and project should work`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        val hankeTunnus = "HAI-123"
        val hankeId = 123
        val userId = currentUserId()
        val uuid = UUID.randomUUID()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
            .returns(true)
        every { attachmentService.add(hankeTunnus, file) }
            .returns(
                AttachmentMetadata(
                    id = uuid,
                    name = "text.txt",
                    createdByUserId = USERNAME,
                    createdAt = DateFactory.getEndDatetime().toLocalDateTime(),
                    AttachmentScanStatus.PENDING,
                    hankeTunnus
                )
            )

        sendAttachment(hankeTunnus, file).andExpect(status().`is`(200))

        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
        verify { attachmentService.add(hankeTunnus, file) }
    }

    fun sendAttachment(tunnus: String, file: MockMultipartFile): ResultActions {
        return mockMvc.perform(
            MockMvcRequestBuilders.multipart("/hankkeet/${tunnus}/liitteet")
                .file(file)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        )
    }
}
