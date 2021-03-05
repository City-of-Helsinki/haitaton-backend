package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.ControllerExceptionHandler
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HankeGeometriaController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
internal class HankeGeometriaControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `create Geometria OK`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val hankeTunnus = "1234567"
        val hankeId = 1
        val savedHankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        savedHankeGeometriat.hankeId = hankeId
        savedHankeGeometriat.version = 1
        every { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) } returns savedHankeGeometriat

        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(savedHankeGeometriat.toJsonString()))

        verify { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) }
    }

    @Test
    fun `create Geometria with invalid HankeID`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val hankeTunnus = "1234567"
        every { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) } throws HankeNotFoundException(hankeTunnus)

        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))

        verify { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) }
    }

    @Test
    fun `create Geometria with service failure`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val hankeTunnus = "1234567"
        every {
            hankeGeometriatService.saveGeometriat(
                hankeTunnus,
                any()
            )
        } throws RuntimeException("Something went wrong")

        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI0002"))

        verify { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) }
    }

    @Test
    fun `create Geometria without Geometria`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.featureCollection = null
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val hankeTunnus = "1234567"
        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andDo { println(it.response.contentAsString) }
            .andExpect(status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
    }

    @Test
    fun `create Geometria without Geometria crs`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        hankeGeometriat.featureCollection?.crs = null
        val hankeTunnus = "1234567"
        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1011"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid Hanke geometry"))
    }

    @Test
    fun `create Geometria with invalid coordinate system`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        hankeGeometriat.featureCollection?.crs?.properties?.set("name", "urn:ogc:def:crs:EPSG::0000")
        val hankeTunnus = "1234567"
        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1013"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorMessage").value("Invalid coordinate system"))

        verify(exactly = 0) { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) }
    }

    @Test
    fun `delete Geometria`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat-delete.json"
            .asJsonResource(HankeGeometriat::class.java)
        val hankeTunnus = "1234567"
        val hankeId = 1
        val savedHankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        savedHankeGeometriat.hankeId = hankeId
        savedHankeGeometriat.version = 1
        every { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) } returns savedHankeGeometriat
        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(savedHankeGeometriat.toJsonString()))

        verify { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) }
    }

    @Test
    fun `get Geometria OK`() {
        val hankeTunnus = "1234567"
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        every { hankeGeometriatService.loadGeometriat(hankeTunnus) } returns hankeGeometriat
        mockMvc.perform(
            get("/hankkeet/$hankeTunnus/geometriat")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.hankeId").value(hankeGeometriat.hankeId!!))
        verify { hankeGeometriatService.loadGeometriat(hankeTunnus) }
    }

    @Test
    fun `get Geometria for missing Hanke`() {
        val hankeTunnus = "1234567"
        every { hankeGeometriatService.loadGeometriat(hankeTunnus) } throws HankeNotFoundException(hankeTunnus)
        mockMvc.perform(
            get("/hankkeet/$hankeTunnus/geometriat")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))
        verify { hankeGeometriatService.loadGeometriat(hankeTunnus) }
    }

    @Test
    fun `get Geometria for missing geometry`() {
        val hankeTunnus = "1234567"
        every { hankeGeometriatService.loadGeometriat(hankeTunnus) } returns null
        mockMvc.perform(
            get("/hankkeet/$hankeTunnus/geometriat")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1015"))
        verify { hankeGeometriatService.loadGeometriat(hankeTunnus) }
    }

    @Test
    fun `get Geometria with internal error`() {
        val hankeTunnus = "1234567"
        every { hankeGeometriatService.loadGeometriat(hankeTunnus) } throws RuntimeException()
        mockMvc.perform(
            get("/hankkeet/$hankeTunnus/geometriat")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI0002"))
        verify { hankeGeometriatService.loadGeometriat(hankeTunnus) }
    }
}
