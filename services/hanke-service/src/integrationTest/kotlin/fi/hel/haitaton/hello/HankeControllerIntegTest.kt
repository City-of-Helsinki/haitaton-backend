package fi.hel.haitaton.hello

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Testing the Hnake Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */

@SpringBootTest
@AutoConfigureMockMvc
@EnableAutoConfiguration
class HankeControllerIntegTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `When hankeId not given then Bad Request`() {

        mockMvc.perform(get("/hankkeet/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)

    }

    //TODO: needs mocking when we have actual logic for returning the particular Hanke
    @Test
    fun `When hankeId is given then return Hanke with it`() {

        mockMvc.perform(get("/hankkeet?hankeId=jotain").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `Add Hanke and return it with newly created hankeId`() {
        data class RestDataHanke(var hankeId: String, val name: String, val implStartDate: String, val implEndDate: String, val owner: String, val phase: Int)

        val hankeName = "Mannerheimintien remontti remonttinen"
        //make Hanke for request (timestamps are Strings for now)
        val hankeToBeAdded = RestDataHanke("kissa", hankeName, "20201212", "20201212", "Risto", 1)

        val objectMapper = ObjectMapper()
        val hankeJSON = objectMapper.writeValueAsString(hankeToBeAdded)

        mockMvc.perform(post("/hankkeet").contentType(MediaType.APPLICATION_JSON).content(hankeJSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(hankeName))
    }

}


