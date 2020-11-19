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
    fun `When hankeTunnus not given then Bad Request`() {
        mockMvc.perform(get("/hankkeet/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
    }

    @Test
    fun `When hankeTunnus is given then return Hanke with it (GET)`() {
        // faking the service call
        Mockito.`when`(hankeService.loadHanke(mockedHankeTunnus))
                .thenReturn(Hanke(123, mockedHankeTunnus, true, "Hämeentien perusparannus ja katuvalot", "lorem ipsum dolor sit amet...",
                        getCurrentTimeUTC(), getCurrentTimeUTC(), "OHJELMOINTI",
                        1, "Risto", getCurrentTimeUTC(), null, null, SaveType.DRAFT))

        mockMvc.perform(get("/hankkeet?hankeId=" + mockedHankeTunnus).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nimi")
                        .value("Hämeentien perusparannus ja katuvalot"))

    }

    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"
        var hankeToBeAdded = Hanke(id = null, hankeTunnus = "", nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = "OHJELMOINTI",
                version = null, creatorUserId = "Tiina", createdAt = null, modifierUserId = null, modifiedAt = null, saveType = SaveType.DRAFT)

        // faking the service call
        Mockito.`when`(hankeService.createHanke(hankeToBeAdded))
                .thenReturn(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        //TODO: Should we make sure that the hankeTunnus is returned?
    }

    @Test
    fun `Update Hanke with partial data and return it (PUT)`() {
        var name = "kissahanke"
        // initializing only part of the data for Hanke
        val hankeToBeAdded = Hanke(id = null, hankeTunnus = null, nimi = name, kuvaus = null,
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = "OHJELMOINTI",
                version = null, creatorUserId = "Tiina", createdAt = null, modifierUserId = null, modifiedAt = null, saveType = SaveType.DRAFT)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        // faking the service call
        Mockito.`when`(hankeService.updateHanke(hankeToBeAdded))
                .thenReturn(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/hankeId=idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nimi").value(name))
    }

    @Test
    fun `test that the validation gives error and Bad Request is returned when creatorUserId is empty`() {
        var hankeToBeAdded = Hanke(id = null, hankeTunnus = "idHankkeelle123", nimi = "", kuvaus = null,
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = "RAKENTAMINEN",
                version = null, creatorUserId = "", createdAt = null, modifierUserId = null, modifiedAt = null, saveType = SaveType.DRAFT)
        // mock HankeService response
        Mockito.`when`(hankeService.createHanke(hankeToBeAdded)).thenReturn(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

         mockMvc.perform(put("/hankkeet/hankeId=idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

}
