package fi.hel.haitaton.hanke

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.geojson.FeatureCollection
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
internal class HankeGeometriaControllerITests(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    private lateinit var hankeGeometriaService: HankeGeometriaService

    @Test
    fun `create Geometria OK`() {
        val content = Files.readString(Paths.get("src/test/resources/featureCollection.json"))
        val hankeId = "1234567"
        every { hankeGeometriaService.saveGeometria(hankeId, any()) } just runs

        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent)
                .andExpect(content().string(""))

        verify { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `create Geometria with invalid HankeID`() {
        val content = Files.readString(Paths.get("src/test/resources/featureCollection.json"))
        val hankeId = "1234567"
        every { hankeGeometriaService.saveGeometria(hankeId, any()) } throws HankeNotFoundException(hankeId)

        mockMvc.perform(post("/hankkeet/${hankeId}/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))

        verify { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `create Geometria with service failure`() {
        val content = Files.readString(Paths.get("src/test/resources/featureCollection.json"))
        val hankeId = "1234567"
        every { hankeGeometriaService.saveGeometria(hankeId, any()) } throws RuntimeException("Something went wrong")

        mockMvc.perform(post("/hankkeet/${hankeId}/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1012"))

        verify { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `create Geometria without Geometria`() {
        val hankeId = "1234567"
        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
    }

    @Test
    fun `create Geometria with invalid coordinate system`() {
        val featureCollection = objectMapper.readValue(Files.readString(Paths.get("src/test/resources/featureCollection.json")), FeatureCollection::class.java)
        featureCollection.crs.properties["name"] = "urn:ogc:def:crs:EPSG::0000"
        val hankeId = "1234567"
        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(featureCollection.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
    }
}
