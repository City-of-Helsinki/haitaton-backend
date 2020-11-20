package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*



/**
 * Testing the Hanke Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */

@WebMvcTest
@Import(Configuration::class)
class HankeControllerITests(@Autowired val mockMvc: MockMvc) {

    val mockedHankeTunnus = "GHSFG123"

    lateinit var hankeController: HankeController

    @MockBean
    lateinit var hankeService: HankeService  // faking this calls

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.initMocks(this);
        hankeController = HankeController(hankeService)
    }


    @Test
    fun `When hankeId not given for fetching then error`() {

        mockMvc.perform(get("/hankkeet/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError)

    }

    @Test
    fun `When hankeTunnus is given then return Hanke with it (GET)`() {


        //faking the service call
      
       Mockito.`when`(hankeService.loadHanke(mockedHankeTunnus))
                .thenReturn(Hanke(123, mockedHankeTunnus, true, "Hämeentien perusparannus ja katuvalot", "lorem ipsum dolor sit amet...",
                        getCurrentTimeUTC(), getCurrentTimeUTC(), "OHJELMOINTI",
                        1, "Risto", getCurrentTimeUTC(), null, null, SaveType.DRAFT))

        mockMvc.perform(get("/hankkeet/ + mockedHankeTunnus).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nimi")
                        .value("Hämeentien perusparannus ja katuvalot"))

    }

    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"
        var hankeToBeMocked = Hanke(hankeId = "", name = hankeName, isYKTHanke = false, startDate = ZonedDateTime.now(), endDate = ZonedDateTime.now(), owner = "Tiina", phase = 0)

        //faking the service call
        Mockito.`when`(hankeService.save(hankeToBeMocked))
                .thenReturn(hankeToBeMocked)

        class HankeTemp(
                var hankeId: String?,
                var isYKTHanke: Boolean?,
                var name: String?,
                var startDate: String?,
                var endDate: String?,
                val owner: String,
                var phase: Int?)

        var hankeToBeAdded = HankeTemp(mockedHankeId, true, "Hämeentien perusparannus ja katuvalot",
                "2020-11-20T08:16:58.2533652+02:00", "2020-12-20T08:16:58.2533652+02:00", "Risto", 1)
        var hankeToBeAdded = Hanke(id = null, hankeTunnus = "", nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = "OHJELMOINTI",
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        // faking the service call
        Mockito.`when`(hankeService.createHanke(hankeToBeAdded))
                .thenReturn(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)
          // have tried also with JSON:
           //     "{\"hankeId\":\"string\",\"name\":\"string\",\"startDate\":\"2020-11-20T08:16:58.2533652+02:00\"," +
             //           "\"endDate\":\"2020-11-20T08:16:58.2533652+02:00\",\"owner\":\"string\",\"phase\":0,\"ykthanke\":true}"

        var reso = mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
        //          .andExpect(content().contentType(MediaType.APPLICATION_JSON)) //TODO: why is content empty / not JSON
        //TODO: Should we make sure that the hankeId is returned?
    }

    @Test
    fun `Update Hanke with data and return it (PUT)`() {

        val hankeName = "Mannerheimintien remontti remonttinen"
        var hankeToBeMocked = Hanke(hankeId = "", name = hankeName, isYKTHanke = false, startDate = ZonedDateTime.now(), endDate = ZonedDateTime.now(), owner = "Tiina", phase = 0)

        //faking the service call
        Mockito.`when`(hankeService.save(hankeToBeMocked))
                .thenReturn(hankeToBeMocked)

        class HankeTemp(
                var hankeId: String?,
                var isYKTHanke: Boolean?,
                var name: String?,
                var startDate: String?,
                var endDate: String?,
                val owner: String,
                var phase: Int?)

        var hankeToBeAdded = HankeTemp(mockedHankeId, true, "Hämeentien perusparannus ja katuvalot",
                "2020-11-20T08:16:58.2533652+02:00", "2020-12-20T08:16:58.2533652+02:00", "Risto", 3)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        //TODO: Should we make sure that the hankeTunnus is returned?
    }

    @Test
    fun `Update Hanke with partial data and return it (PUT)`() {
        var name = "kissahanke"
        // initializing only part of the data for Hanke
        val hankeToBeAdded = Hanke(id = null, hankeTunnus = null, nimi = name, kuvaus = null,
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = "OHJELMOINTI",
                version = null, createdBy = "Tiina", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
        /*    .andExpect(content().contentType(MediaType.APPLICATION_JSON)) //TODO: why is content empty / not JSO
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(hankeName))
            */
    }

    @Test
    fun `test that the validation gives error and Bad Request is returned when creatorUserId is empty`() {
        var hankeToBeAdded = Hanke(id = null, hankeTunnus = "idHankkeelle123", nimi = "", kuvaus = null,
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = "RAKENTAMINEN",
                version = null, createdBy = "", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)
        // mock HankeService response
        Mockito.`when`(hankeService.createHanke(hankeToBeAdded)).thenReturn(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

}
