package fi.hel.haitaton.hanke

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths

@SpringBootTest(properties = ["spring.liquibase.enabled=false"])  // Without this, the JPA repository service won't be found as a bean
@AutoConfigureMockMvc
internal class HankeGeometriaControllerITests(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    private lateinit var hankeGeometriaService: HankeGeometriaService

    @Test
    fun `create Geometria OK`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        val hankeId = "1234567"
        val savedHankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        savedHankeGeometriat.hankeId = hankeId
        savedHankeGeometriat.version = 1
        every { hankeGeometriaService.saveGeometria(hankeId, any()) } returns savedHankeGeometriat

        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().string(savedHankeGeometriat.toJsonString()))

        verify { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `create Geometria with invalid HankeID`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        val hankeId = "1234567"
        every { hankeGeometriaService.saveGeometria(hankeId, any()) } throws HankeNotFoundException(hankeId)

        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))

        verify { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `create Geometria with service failure`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        val hankeId = "1234567"
        every { hankeGeometriaService.saveGeometria(hankeId, any()) } throws RuntimeException("Something went wrong")

        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1012"))

        verify { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `create Geometria without Geometria`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.featureCollection = null
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        val hankeId = "1234567"
        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
    }

    @Test
    fun `create Geometria without Geometria features`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        hankeGeometriat.featureCollection?.features = null
        val hankeId = "1234567"
        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid Hanke geometry"))
    }

    @Test
    fun `create Geometria without Geometria crs`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        hankeGeometriat.featureCollection?.crs = null
        val hankeId = "1234567"
        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid Hanke geometry"))
    }

    @Test
    fun `create Geometria with invalid coordinate system`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.updatedAt = null
        hankeGeometriat.featureCollection?.crs?.properties?.set("name", "urn:ogc:def:crs:EPSG::0000")
        val hankeId = "1234567"
        mockMvc.perform(post("/hankkeet/$hankeId/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1013"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid coordinate system"))

        verify(exactly = 0) { hankeGeometriaService.saveGeometria(hankeId, any()) }
    }

    @Test
    fun `get Geometria OK`() {
        val hankeId = "1234567"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        every { hankeGeometriaService.loadGeometria(hankeId) } returns hankeGeometriat
        mockMvc.perform(get("/hankkeet/$hankeId/geometriat")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.hankeId").value(hankeId))
        verify { hankeGeometriaService.loadGeometria(hankeId) }
    }

    @Test
    fun `get Geometria for missing Hanke`() {
        val hankeId = "1234567"
        every { hankeGeometriaService.loadGeometria(hankeId) } throws HankeNotFoundException(hankeId)
        mockMvc.perform(get("/hankkeet/$hankeId/geometriat")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))
        verify { hankeGeometriaService.loadGeometria(hankeId) }
    }

    @Test
    fun `get Geometria for missing geometry`() {
        val hankeId = "1234567"
        every { hankeGeometriaService.loadGeometria(hankeId) } returns null
        mockMvc.perform(get("/hankkeet/$hankeId/geometriat")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1015"))
        verify { hankeGeometriaService.loadGeometria(hankeId) }
    }

    @Test
    fun `get Geometria with internal error`() {
        val hankeId = "1234567"
        every { hankeGeometriaService.loadGeometria(hankeId) } throws RuntimeException()
        mockMvc.perform(get("/hankkeet/$hankeId/geometriat")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError)
                .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1014"))
        verify { hankeGeometriaService.loadGeometria(hankeId) }
    }
}
