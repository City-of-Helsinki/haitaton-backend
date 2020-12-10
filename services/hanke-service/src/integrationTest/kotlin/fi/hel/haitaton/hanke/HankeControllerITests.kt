package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Testing the Hanke Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */
@WebMvcTest
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class HankeControllerITests(@Autowired val mockMvc: MockMvc) {

    private val mockedHankeTunnus = "GHSFG123"

    @Autowired
    lateinit var hankeService: HankeService  //faking these calls

    @Test
    fun `When hankeId not given for fetching then error`() {

        mockMvc.perform(get("/hankkeet/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError)

    }

    @Test
    fun `When hankeTunnus is given then return Hanke with it (GET)`() {

        // faking the service call
        every { hankeService.loadHanke(any()) }.returns(Hanke(123, mockedHankeTunnus, true, "Hämeentien perusparannus ja katuvalot", "lorem ipsum dolor sit amet...",
                getDatetimeAlku(), getDatetimeLoppu(), Vaihe.OHJELMOINTI, null,
                1, "Risto", getCurrentTimeUTC(), null, null, SaveType.DRAFT))


        mockMvc.perform(get("/hankkeet/$mockedHankeTunnus").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nimi")
                        .value("Hämeentien perusparannus ja katuvalot"))
        verify { hankeService.loadHanke(any()) }

    }

    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"

        val hankeToBeMocked = Hanke(id = null, hankeTunnus = null, nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        // faking the service call
        every { hankeService.createHanke(any()) }.returns(hankeToBeMocked)

        val content = hankeToBeMocked.toJsonString()

        val expectedContent = hankeToBeMocked.apply { hankeTunnus = mockedHankeTunnus }.toJsonString()

        mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.content().json(expectedContent))
                .andExpect(jsonPath("$.hankeTunnus").value(mockedHankeTunnus))
        verify { hankeService.createHanke(any()) }

    }

    @Test
    fun `Update Hanke with data and return it (PUT)`() {

        val hankeName = "Mannerheimintien remontti remonttinen"

        // initializing only part of the data for Hanke

        val hankeToBeUpdated = Hanke(id = 23, hankeTunnus = "idHankkeelle123", nimi = hankeName, kuvaus = "kuvaus",
                onYKTHanke = false, alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        // faking the service call
        every { hankeService.updateHanke(any()) }.returns(hankeToBeUpdated)

        val content = hankeToBeUpdated.toJsonString()

        mockMvc.perform(put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nimi").value(hankeName))

        verify { hankeService.updateHanke(any()) }
    }

    @Test
    fun `Add Hanke and HankeYhteystiedot and return it with newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"

        val hankeToBeMocked = Hanke(id = null, hankeTunnus = null, nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        //HankeYhteystieto Omistaja added
        hankeToBeMocked.omistajat = arrayListOf(
                HankeYhteystieto(null, "Pekkanen", "Pekka",
                        "pekka@pekka.fi", "3212312", null,
                        "Kaivuri ja mies", null, null, null,
                        null, null))

        val content = hankeToBeMocked.toJsonString()

        //changing some return values
        val expectedHanke = hankeToBeMocked
                .apply {
                    hankeTunnus = mockedHankeTunnus
                    id = 12
                    omistajat[0].id = 3
                }

        //faking the service call
        every { hankeService.createHanke(any()) }.returns(expectedHanke)
        val expectedContent = expectedHanke.toJsonString()
        mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.content().json(expectedContent))
                .andExpect(jsonPath("$.hankeTunnus").value(mockedHankeTunnus))
        verify { hankeService.createHanke(any()) }

    }

    @Test
    fun `Try add with invalid data and get validation error (POST)`() {

        val hankeName = "Mannerheimintien remontti remonttinen"

        val hankeInvalid = Hanke(id = null, hankeTunnus = null, nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        //HankeYhteystieto Omistaja added
        hankeInvalid.omistajat = arrayListOf(
                HankeYhteystieto(null, "", "",
                        "", "3212312", null,
                        "Kaivuri ja mies", null, null, null,
                        null, null))
        //HankeYhteystieto Omistaja added
        hankeInvalid.toteuttajat = arrayListOf(
                HankeYhteystieto(null, "", "",
                        "", "3212312", null,
                        "Kaivuri ja toteuttaja", null, null, null,
                        null, null))

        mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(hankeInvalid.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1002"))
    }


    @Test
    fun `test tyomaa and haitat-fields roundtrip`() {
        val hankeName = "Mannerheimintien remontti remonttinen"
        // Initializing all fields
        val hankeToBeUpdated = Hanke(id = 23, hankeTunnus = "idHankkeelle123", nimi = hankeName, kuvaus = "kuvaus",
                onYKTHanke = false, alkuPvm = getDatetimeAlku(), loppuPvm = getDatetimeLoppu(), vaihe = Vaihe.SUUNNITTELU, suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
                version = 0, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)
        hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
        hankeToBeUpdated.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hankeToBeUpdated.haittaAlkuPvm = getDatetimeAlku()
        hankeToBeUpdated.haittaLoppuPvm = getDatetimeLoppu()
        hankeToBeUpdated.kaistaHaitta = Haitta04.KAKSI
        hankeToBeUpdated.kaistaPituusHaitta = Haitta04.NELJA
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

        // Call it and check results
        mockMvc.perform(put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.content().json(expectedContent))
                // These might be redundant, but at least it is clear what we're checking here:
                .andExpect(jsonPath("$.tyomaaKatuosoite").value("Testikatu 1"))
                .andExpect(jsonPath("$.kaistaHaitta").value("KAKSI")) // Note, here as string, not the enum.
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
        val hankeToBeMocked = Hanke(id = null, hankeTunnus = null, nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = datetimeAlku, loppuPvm = datetimeLoppu, vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = SuunnitteluVaihe.KATUSUUNNITTELU_TAI_ALUEVARAUS,
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)
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
        expectedDateAlkuJson = expectedDateAlkuJson.substring(1, expectedDateAlkuJson.length-1)
        expectedDateLoppuJson = expectedDateLoppuJson.substring(1, expectedDateLoppuJson.length-1)

        // faking the service call
        every { hankeService.createHanke(any()) }.returns(expectedHanke)

        // Call it and check results
        mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.content().json(expectedContent))
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

}
