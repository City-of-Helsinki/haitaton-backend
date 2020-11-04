package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.Spy
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

    @MockBean
    private val mockHankeService: HankeService = Mockito.mock(HankeService::class.java)


    /*  @BeforeEach
      fun setUp() {
          Mockito.`when`(mockHankeService.loadHanke(mockedHankeId))
                  .thenReturn(Hanke(mockedHankeId, true, "Mannerheimintien remontti remonttinen", ZonedDateTime.now(), ZonedDateTime.now(), "Risto", 1))
      }
  */

    @BeforeEach
    fun setup() {
        //    Mockito.reset(mockHankeService)
        //   MockitoAnnotations.initMocks(this);

    }

    @Test
    fun `When hankeId not given then Bad Request`() {

        mockMvc.perform(get("/hankkeet/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)

    }


    //TODO: needs mocking when we have actual logic for returning the particular Hanke
    @Test
    fun `When hankeId is given then return Hanke with it (GET)`() {

        //     given(hankeService.loadHanke(mockedHankeId)).willReturn(Hanke(mockedHankeId, true, "Hämeentien perusparannus ja katuvalot"
        //           , ZonedDateTime.now(), ZonedDateTime.now(), "Risto", 1));


        Mockito.`when`(mockHankeService.loadHanke(mockedHankeId))
                .thenReturn(Hanke(mockedHankeId, true, "Hämeentien perusparannus ja katuvalot"
                        , ZonedDateTime.now(), ZonedDateTime.now(), "Risto", 1))

        mockMvc.perform(get("/hankkeet?hankeId=" + mockedHankeId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `Add Hanke and return it with newly created hankeId (POST)`() {
        data class RestDataHanke(var hankeId: String, val name: String, val implStartDate: String, val implEndDate: String, val owner: String, val phase: Int)

        val hankeName = "Mannerheimintien remontti remonttinen"
        val hankeToBeAdded = RestDataHanke("kissa", hankeName, "20201212", "20201212", "Risto", 1)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(post("/hankkeet").contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(hankeName))
    }

    @Test
    fun `Update Hanke with partial data and return it (PUT)`() {

        //initializing only part of the data for Hanke
        var name = "kissahanke"
        val hankeToBeAdded = Hanke(hankeId = null, name = name, isYKTHanke = false, startDate = null, endDate = null, owner = "Tiina", phase = null)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/hankeId=idHankkeelle123").contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(name))
    }

}


