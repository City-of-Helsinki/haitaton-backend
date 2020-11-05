package fi.hel.haitaton.hanke

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest
@AutoConfigureMockMvc
@EnableAutoConfiguration
class HankeGeometriaControllerITests(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `create Geometria OK`() {
        val content = Files.readString(Paths.get("src/test/resources/featureCollection.json"))

        mockMvc.perform(post("/hankkeet/1234567/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent)
                .andExpect(content().string(""))
    }

    @Test
    fun `create Geometria with invalid HankeID`() {
        val content = Files.readString(Paths.get("src/test/resources/featureCollection.json"))

        mockMvc.perform(post("/hankkeet/INVALID/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))
    }

    @Test
    fun `create Geometria without Geometria`() {
        mockMvc.perform(post("/hankkeet/INVALID/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
    }
}


