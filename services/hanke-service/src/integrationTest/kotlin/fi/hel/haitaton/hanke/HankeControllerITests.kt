package fi.hel.haitaton.hanke

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.domain.Hanke
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*


/**
 * Testing the Hanke Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */

@WebMvcTest
@Import(Configuration::class)
class HankeControllerITests(@Autowired val mockMvc: MockMvc) {

    val mockedHankeTunnus = "GHSFG123"

    @MockkBean
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
                getCurrentTimeUTC(), getCurrentTimeUTC(), Vaihe.OHJELMOINTI, null,
                1, "Risto", getCurrentTimeUTC(), null, null, SaveType.DRAFT))

        mockMvc.perform(get("/hankkeet/" + mockedHankeTunnus).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.nimi")
                        .value("Hämeentien perusparannus ja katuvalot"))
        verify { hankeService.loadHanke(any()) }

    }


    @Test
    fun `Add Hanke and return it newly created hankeTunnus (POST)`() {
        val hankeName = "Mannerheimintien remontti remonttinen"

        var hankeToBeMocked = Hanke(id = null, hankeTunnus = null, nimi = hankeName, kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = getCurrentTimeUTC(), loppuPvm = getCurrentTimeUTC(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
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

        // TODO: Should we make sure that the hankeId is returned?
    }

    @Test
    fun `Update Hanke with data and return it (PUT)`() {

        val hankeName = "Mannerheimintien remontti remonttinen"

        // initializing only part of the data for Hanke
        val hankeToBeUpdated = Hanke(id = 23, hankeTunnus = "idHankkeelle123", nimi = hankeName, kuvaus = "kuvaus",
                onYKTHanke = false, alkuPvm = getCurrentTimeUTC(), loppuPvm = getCurrentTimeUTC(), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
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
    fun `test that the validation gives error and Bad Request is returned when creatorUserId is empty`() {
        var hankeToBeAdded = Hanke(id = null, hankeTunnus = "idHankkeelle123", nimi = "", kuvaus = null,
                onYKTHanke = false, alkuPvm = null, loppuPvm = null, vaihe = Vaihe.RAKENTAMINEN, suunnitteluVaihe = null,
                version = null, createdBy = "", createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        every { hankeService.createHanke(any()) }.returns(hankeToBeAdded)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(put("/hankkeet/idHankkeelle123")
                .contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))

    }

}
