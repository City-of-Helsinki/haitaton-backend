package fi.hel.haitaton.hanke


import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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

    val mockedHankeId = "GHSFG123"

    lateinit var hankeController: HankeController

    @MockBean
    lateinit var hankeService: HankeService  //faking this calls

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
    fun `When hankeId is given then return Hanke with it (GET)`() {


        //faking the service call
        Mockito.`when`(hankeService.loadHanke(mockedHankeId))
                .thenReturn(Hanke(mockedHankeId, true, "Hämeentien perusparannus ja katuvalot", ZonedDateTime.now(), ZonedDateTime.now(), "Risto", 1))

        mockMvc.perform(get("/hankkeet/" + mockedHankeId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name")
                        .value("Hämeentien perusparannus ja katuvalot"))

    }

    @Test
    fun `Add Hanke and return it newly created hankeId (POST)`() {

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
    fun `test that the validation gives error and Bad Request is returned when owner is empty`() {

        var hankeToBeAdded = Hanke(hankeId = "idHankkeelle123", name = "", isYKTHanke = false, startDate = null, endDate = null, owner = "", phase = 3)
        //mock HankeService response
        Mockito.`when`(hankeService.save(hankeToBeAdded)).thenReturn(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

}


