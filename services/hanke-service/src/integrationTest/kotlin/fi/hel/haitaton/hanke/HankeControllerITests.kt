package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionProfiles
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.every
import io.mockk.verify
import org.geojson.FeatureCollection
import org.hamcrest.Matchers.hasSize
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
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

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

    @Autowired
    lateinit var hankeService: HankeService  // faking these calls

    @Autowired
    lateinit var permissionService: PermissionService

    @Autowired
    lateinit var hankeGeometriatService: HankeGeometriatService

    @Test
    fun `When hankeTunnus is given then return Hanke with it (GET)`() {

        // faking the service call
        every { hankeService.loadHanke(mockedHankeTunnus) }
            .returns(
                Hanke(
                    123,
                    mockedHankeTunnus,
                    true,
                    "Hämeentien perusparannus ja katuvalot", "lorem ipsum dolor sit amet...",
                    getDatetimeAlku(),
                    getDatetimeLoppu(),
                    Vaihe.OHJELMOINTI,
                    null,
                    1,
                    "Risto",
                    getCurrentTimeUTC(),
                    null,
                    null,
                    SaveType.DRAFT
                )
            )

        mockMvc.perform(get("/hankkeet/$mockedHankeTunnus").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(
                jsonPath("$.nimi")
                    .value("Hämeentien perusparannus ja katuvalot")
            )
        verify { hankeService.loadHanke(mockedHankeTunnus) }
    }

    @Test
    fun `When calling get without parameters then return all Hanke data without geometry`() {
        // because test call has limitation and automatically creates object for call, we need to create
        // "empty" object for init and verify
        val hankeIds = listOf(123,444)
        val permissions = listOf(
            Permission(
                1,
                "test",
                123,
                PermissionProfiles.HANKE_OWNER_PERMISSIONS
            ),
            Permission(
                44,
                "test",
                444,
                listOf(PermissionCode.VIEW)
            )
        )
        // faking the service call with two returned Hanke
        every { hankeService.loadHankkeetByIds(hankeIds) }
            .returns(
                listOf(
                    Hanke(
                        123,
                        mockedHankeTunnus,
                        true,
                        "Hämeentien perusparannus ja katuvalot",
                        "lorem ipsum dolor sit amet...",
                        getDatetimeAlku().minusDays(500),
                        getDatetimeLoppu().minusDays(450),
                        Vaihe.OHJELMOINTI,
                        null,
                        1,
                        "Risto",
                        getCurrentTimeUTC(),
                        null,
                        null,
                        SaveType.DRAFT
                    ),
                    Hanke(
                        444,
                        "hanketunnus2",
                        true,
                        "Esplanadin viemäröinti",
                        "lorem ipsum dolor sit amet...",
                        getDatetimeAlku(),
                        getDatetimeLoppu(),
                        Vaihe.OHJELMOINTI,
                        null,
                        1,
                        "Risto",
                        getCurrentTimeUTC(),
                        null,
                        null,
                        SaveType.DRAFT
                    )
                )
            )
        every { permissionService.getPermissionsByUserId("test") }.returns(permissions)

        // we check that we get the two hankeTunnus we expect
        mockMvc.perform(get("/hankkeet").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].hankeTunnus").value(mockedHankeTunnus))
            .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
            .andExpect(jsonPath("$[0].id").value(123))
            .andExpect(jsonPath("$[1].id").value(444))
            .andExpect(jsonPath("$[0].geometriat").doesNotExist())
            .andExpect(jsonPath("$[1].geometriat").doesNotExist())
            .andExpect(jsonPath("$[0].permissions").isArray)
            .andExpect(jsonPath("$[0].permissions").value(hasSize<Array<Any>>(PermissionProfiles.HANKE_OWNER_PERMISSIONS.size)))
            .andExpect(jsonPath("$[1].permissions").isArray)
            .andExpect(jsonPath("$[1].permissions").value(hasSize<Array<Any>>(1)))

        verify { hankeService.loadHankkeetByIds(hankeIds) }
    }

    @Test
    fun `When calling get with geometry=true then return all Hanke data with geometry`() {
        // faking the service call with two returned Hanke
        val hankeIds = listOf(123,444)
        val permissions = listOf(
            Permission(
                1,
                "test",
                123,
                PermissionProfiles.HANKE_OWNER_PERMISSIONS
            ),
            Permission(
                2,
                "test",
                444,
                listOf(PermissionCode.VIEW)
            )
        )
        every { hankeService.loadHankkeetByIds(hankeIds) }
            .returns(
                listOf(
                    Hanke(123, mockedHankeTunnus),
                    Hanke(444, "hanketunnus2")
                )
            )
        every { hankeGeometriatService.loadGeometriat(Hanke(123, mockedHankeTunnus)) }
            .returns(
                HankeGeometriat(
                    1,
                    123,
                    FeatureCollection()
                )
            )
        every { hankeGeometriatService.loadGeometriat(Hanke(444, "hanketunnus2")) }
            .returns(
                HankeGeometriat(
                    2,
                    444,
                    FeatureCollection()
                )
            )
        every { permissionService.getPermissionsByUserId("test") }.returns(permissions)

        // we check that we get the two hankeTunnus and geometriat we expect
        mockMvc.perform(get("/hankkeet?geometry=true").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].hankeTunnus").value(mockedHankeTunnus))
            .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
            .andExpect(jsonPath("$[0].id").value(123))
            .andExpect(jsonPath("$[1].id").value(444))
            .andExpect(jsonPath("$[0].geometriat.id").value(1))
            .andExpect(jsonPath("$[1].geometriat.id").value(2))
            .andExpect(jsonPath("$[0].permissions").isArray)
            .andExpect(jsonPath("$[0].permissions").value(hasSize<Array<Any>>(PermissionProfiles.HANKE_OWNER_PERMISSIONS.size)))
            .andExpect(jsonPath("$[1].permissions").isArray)
            .andExpect(jsonPath("$[1].permissions").value(hasSize<Array<Any>>(1)))


        verify { hankeService.loadHankkeetByIds(hankeIds) }
        verify { hankeGeometriatService.loadGeometriat(Hanke(123, mockedHankeTunnus)) }
        verify { hankeGeometriatService.loadGeometriat(Hanke(444, "hanketunnus2")) }
    }

    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"

        val hankeToBeMocked = Hanke(
            id = 12,
            hankeTunnus = null,
            nimi = hankeName,
            kuvaus = "lorem ipsum dolor sit amet...",
            onYKTHanke = false,
            alkuPvm = getDatetimeAlku(),
            loppuPvm = getDatetimeLoppu(),
            vaihe = Vaihe.OHJELMOINTI,
            suunnitteluVaihe = null,
            version = null,
            createdBy = "test",
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        )
        val permission = Permission(
            1,
            "test",
            12,
            PermissionProfiles.HANKE_OWNER_PERMISSIONS
        )

        // faking the service call
        every { hankeService.createHanke(any()) }.returns(hankeToBeMocked)
        every { permissionService.setPermission(any(), any(), PermissionProfiles.HANKE_OWNER_PERMISSIONS) }.returns(permission)

        val content = hankeToBeMocked.toJsonString()

        val expectedContent = hankeToBeMocked.apply { hankeTunnus = mockedHankeTunnus }.toJsonString()

        mockMvc.perform(
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
    }

    @Test
    fun `Update Hanke with data and return it (PUT)`() {

        val hankeName = "Mannerheimintien remontti remonttinen"

        // initializing only part of the data for Hanke

        val hankeToBeUpdated = Hanke(
            id = 23,
            hankeTunnus = "idHankkeelle123",
            nimi = hankeName,
            kuvaus = "kuvaus",
            onYKTHanke = false,
            alkuPvm = getDatetimeAlku(),
            loppuPvm = getDatetimeLoppu(),
            vaihe = Vaihe.OHJELMOINTI,
            suunnitteluVaihe = null,
            version = null,
            createdBy = "Tiina",
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        )

        // faking the service call
        every { hankeService.updateHanke(any()) }.returns(hankeToBeUpdated)
        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)

        val content = hankeToBeUpdated.toJsonString()

        mockMvc.perform(
            put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(content)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.nimi").value(hankeName))

        verify { hankeService.updateHanke(any()) }
    }

    @Test
    fun `Add Hanke and HankeYhteystiedot and return it with newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"

        val hankeToBeMocked = Hanke(
            id = null,
            hankeTunnus = null,
            nimi = hankeName,
            kuvaus = "lorem ipsum dolor sit amet...",
            onYKTHanke = false,
            alkuPvm = getDatetimeAlku(),
            loppuPvm = getDatetimeLoppu(),
            vaihe = Vaihe.OHJELMOINTI,
            suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
            version = null,
            createdBy = "Tiina",
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        )

        // HankeYhteystieto Omistaja added
        hankeToBeMocked.omistajat = arrayListOf(
            HankeYhteystieto(
                null,
                "Pekkanen",
                "Pekka",
                "pekka@pekka.fi",
                "3212312",
                null,
                "Kaivuri ja mies",
                null,
                null,
                null,
                null,
                null
            )
        )

        val content = hankeToBeMocked.toJsonString()

        // changing some return values
        val expectedHanke = hankeToBeMocked
            .apply {
                hankeTunnus = mockedHankeTunnus
                id = 12
                omistajat[0].id = 3
            }

        val permission = Permission(
            1,
            "Tiina",
            12,
            PermissionProfiles.HANKE_OWNER_PERMISSIONS
        )
        // faking the service calls
        every { permissionService.setPermission(any(), any(), PermissionProfiles.HANKE_OWNER_PERMISSIONS) }.returns(permission)
        every { hankeService.createHanke(any()) }.returns(expectedHanke)
        val expectedContent = expectedHanke.toJsonString()
        mockMvc.perform(
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
    }

    @Test
    fun `test tyomaa and haitat-fields roundtrip`() {
        val hankeName = "Mannerheimintien remontti remonttinen"
        // Initializing all fields
        val hankeToBeUpdated = Hanke(
            id = 23,
            hankeTunnus = "idHankkeelle123",
            nimi = hankeName,
            kuvaus = "kuvaus",
            onYKTHanke = false,
            alkuPvm = getDatetimeAlku(),
            loppuPvm = getDatetimeLoppu(),
            vaihe = Vaihe.SUUNNITTELU,
            suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
            version = 0,
            createdBy = "Tiina",
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        )
        hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
        hankeToBeUpdated.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hankeToBeUpdated.haittaAlkuPvm = getDatetimeAlku()
        hankeToBeUpdated.haittaLoppuPvm = getDatetimeLoppu()
        hankeToBeUpdated.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        hankeToBeUpdated.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
        hankeToBeUpdated.meluHaitta = Haitta13.YKSI
        hankeToBeUpdated.polyHaitta = Haitta13.KAKSI
        hankeToBeUpdated.tarinaHaitta = Haitta13.KOLME
        val content = hankeToBeUpdated.toJsonString()

        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = getDatetimeAlku().truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
        val expectedDateLoppu = getDatetimeLoppu().truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z
        val expectedHanke = hankeToBeUpdated
            .apply {
                alkuPvm = expectedDateAlku
                loppuPvm = expectedDateLoppu
                haittaAlkuPvm = expectedDateAlku
                haittaLoppuPvm = expectedDateLoppu
            }
        val expectedContent = expectedHanke.toJsonString()

        // faking the service call
        every { hankeService.updateHanke(any()) }.returns(expectedHanke)
        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)

        // Call it and check results
        mockMvc.perform(
            put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(content)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            // These might be redundant, but at least it is clear what we're checking here:
            .andExpect(jsonPath("$.tyomaaKatuosoite").value("Testikatu 1"))
            .andExpect(jsonPath("$.kaistaHaitta").value(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI.name)) // Note, here as string, not the enum.
        verify { hankeService.updateHanke(any()) }
    }

    @Test
    fun `test dates and times do not change on a roundtrip, except for rounding to midnight`() {
        // NOTE: times sent in and returned are expected to be in UTC ("Z" or +00:00 offset)

        // Setup hanke with specific date/times:
        // These time values should reveal possible timezone shifts, and not get affected by database time rounding.
        // (That is, if timezone handling causes even 1 hour shift one or the other, either one of these values
        // will flip over to previous/next day with the date-truncation effect done in the service.)
        val datetimeAlku = getDatetimeAlku()
        val datetimeLoppu = getDatetimeLoppu()
        val hankeName = "Mannerheimintien remontti remonttinen"
        val hankeToBeMocked = Hanke(
            id = null,
            hankeTunnus = null,
            nimi = hankeName,
            kuvaus = "lorem ipsum dolor sit amet...",
            onYKTHanke = false,
            alkuPvm = datetimeAlku,
            loppuPvm = datetimeLoppu,
            vaihe = Vaihe.OHJELMOINTI,
            suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
            version = null,
            createdBy = "Tiina",
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        )
        hankeToBeMocked.haittaAlkuPvm = datetimeAlku
        hankeToBeMocked.haittaLoppuPvm = datetimeLoppu
        val content = hankeToBeMocked.toJsonString()

        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = datetimeAlku.truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
        val expectedDateLoppu = datetimeLoppu.truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z
        val expectedHanke = hankeToBeMocked
            .apply {
                hankeTunnus = mockedHankeTunnus
                id = 12
                alkuPvm = expectedDateAlku
                loppuPvm = expectedDateLoppu
                haittaAlkuPvm = expectedDateAlku
                haittaLoppuPvm = expectedDateLoppu
            }
        val expectedContent = expectedHanke.toJsonString()
        // JSON string versions without quotes:
        var expectedDateAlkuJson = expectedDateAlku.toJsonString()
        var expectedDateLoppuJson = expectedDateLoppu.toJsonString()
        expectedDateAlkuJson = expectedDateAlkuJson.substring(1, expectedDateAlkuJson.length - 1)
        expectedDateLoppuJson = expectedDateLoppuJson.substring(1, expectedDateLoppuJson.length - 1)

        // faking the service call
        every { hankeService.createHanke(any()) }.returns(expectedHanke)

        // Call it and check results
        mockMvc.perform(
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
            // These might be redundant, but at least it is clear what we're checking here:
            .andExpect(jsonPath("$.alkuPvm").value(expectedDateAlkuJson))
            .andExpect(jsonPath("$.loppuPvm").value(expectedDateLoppuJson))
            .andExpect(jsonPath("$.haittaAlkuPvm").value(expectedDateAlkuJson))
            .andExpect(jsonPath("$.haittaLoppuPvm").value(expectedDateLoppuJson))
        verify { hankeService.createHanke(any()) }
    }

    private fun getDatetimeAlku(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getDatetimeLoppu(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
    }

    @Test
    fun `test that flag fields are returned in the response data`() {
        // (Mostly copied from haitta-fields test)
        val hankeName = "Mannerheimintien remontti remonttinen"
        // Initializing all fields
        val hankeToBeUpdated = Hanke(
            id = 23,
            hankeTunnus = "idHankkeelle123",
            nimi = hankeName,
            kuvaus = "kuvaus",
            onYKTHanke = false,
            alkuPvm = getDatetimeAlku(),
            loppuPvm = getDatetimeLoppu(),
            vaihe = Vaihe.SUUNNITTELU,
            suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
            version = 0,
            createdBy = "Tiina",
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.SUBMIT
        )
        hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
        hankeToBeUpdated.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hankeToBeUpdated.haittaAlkuPvm = getDatetimeAlku()
        hankeToBeUpdated.haittaLoppuPvm = getDatetimeLoppu()
        hankeToBeUpdated.kaistaHaitta =
            TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        hankeToBeUpdated.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
        hankeToBeUpdated.meluHaitta = Haitta13.YKSI
        hankeToBeUpdated.polyHaitta = Haitta13.KAKSI
        hankeToBeUpdated.tarinaHaitta = Haitta13.KOLME
        val content = hankeToBeUpdated.toJsonString()

        // Prepare the expected result/return
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = getDatetimeAlku().truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
        val expectedDateLoppu = getDatetimeLoppu().truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z
        val expectedHanke = hankeToBeUpdated
            .apply {
                alkuPvm = expectedDateAlku
                loppuPvm = expectedDateLoppu
                haittaAlkuPvm = expectedDateAlku
                haittaLoppuPvm = expectedDateLoppu
                // Faking that it has geometries:
            }
        // Updating flags as per other field values allow to simulate what the service would do:
        expectedHanke.updateStateFlags()
        val expectedContent = expectedHanke.toJsonString()

        // faking the service call
        every { hankeService.updateHanke(any()) }.returns(expectedHanke)
        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)

        // Call it and check results
        mockMvc.perform(
            put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(content)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(expectedContent))
            // The actual beef of this test function:
            .andExpect(jsonPath("$.tilat.onKaikkiPakollisetLuontiTiedot").value(true))
            .andExpect(jsonPath("$.tilat.onViereisiaHankkeita").value(false))
            .andExpect(jsonPath("$.tilat.onAsiakasryhmia").value(false))
        verify { hankeService.updateHanke(any()) }
    }

    @Test
    fun `exception in Hanke creation causes a 500 Internal Server Error response with specific HankeError`() {
        val hanke = Hanke(
            "Testihanke",
            ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, TZ_UTC),
            ZonedDateTime.of(2021, 12, 31, 0, 0, 0, 0, TZ_UTC),
            Vaihe.OHJELMOINTI,
            SaveType.AUTO
        )
        // faking the service call
        every { hankeService.createHanke(any()) } throws RuntimeException("Some error")

        // Call it and check results
        mockMvc.perform(
            post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(hanke.toJsonString())
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isInternalServerError)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""{"errorCode": "HAI0002", "errorMessage": "Internal error"}"""))

        verify { hankeService.createHanke(any()) }
    }
}
