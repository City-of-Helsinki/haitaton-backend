package fi.hel.haitaton.hanke


import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.ZonedDateTime


/**
 * Testing the Hnake Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */

@SpringBootTest
@AutoConfigureMockMvc
@EnableAutoConfiguration
class HankeControllerITests(@Autowired val mockMvc: MockMvc) {

    val mockedHankeId = "GHSFG123"

    @InjectMocks
    lateinit var hankeController: HankeController

    @MockBean
    lateinit var hankeService: HankeService  //faking this calls

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    fun `When hankeId not given then Bad Request`() {

        mockMvc.perform(get("/hankkeet/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)

    }

    @Test
    fun `When hankeId is given then return Hanke with it (GET)`() {

        //faking the service call
        Mockito.`when`(hankeService.loadHanke(mockedHankeId))
                .thenReturn(Hanke(mockedHankeId, true, "Hämeentien perusparannus ja katuvalot"
                        , ZonedDateTime.now(), ZonedDateTime.now(), "Risto", 1))

        mockMvc.perform(get("/hankkeet?hankeId=" + mockedHankeId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name")
                        .value("Hämeentien perusparannus ja katuvalot"))

    }

    @Test
    fun `Add Hanke and return it newly created hankeId (POST)`() {

        val hankeName = "Mannerheimintien remontti remonttinen"
        var hankeToBeAdded = Hanke(hankeId = "", name = hankeName, isYKTHanke = false, startDate = null, endDate = null, owner = "Tiina", phase = null)

        //faking the service call
        Mockito.`when`(hankeService.save(hankeToBeAdded))
                .thenReturn(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        //TODO: Should we make sure that the hankeId is returned?
    }

    @Test
    fun `Update Hanke with partial data and return it (PUT)`() {

        var name = "kissahanke"
        //initializing only part of the data for Hanke
        val hankeToBeAdded = Hanke(hankeId = null, name = name, isYKTHanke = false, startDate = null, endDate = null, owner = "Tiina", phase = null)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/hankeId=idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(name))
    }

}


