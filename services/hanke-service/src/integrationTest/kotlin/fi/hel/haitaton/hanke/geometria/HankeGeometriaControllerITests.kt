package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.domain.Hanke
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
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
    private lateinit var hankeService: HankeService
    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

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

        val hanke = Hanke(hankeId, hankeTunnus)
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { hankeGeometriatService.saveGeometriat(hanke, any()) } returns savedHankeGeometriat

        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(savedHankeGeometriat.toJsonString()))

        verify { hankeGeometriatService.saveGeometriat(hanke, any()) }
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
        every { hankeService.loadHanke(hankeTunnus) } returns null

        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))

        verify { hankeService.loadHanke(hankeTunnus) }
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
        val hankeId = 1
        val hanke = Hanke(hankeId, hankeTunnus)

        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every {
            hankeGeometriatService.saveGeometriat(
                hanke,
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

        verify { hankeGeometriatService.saveGeometriat(hanke, any()) }
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
        clearMocks(hankeGeometriatService)
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

        verify(exactly = 0) { hankeGeometriatService.saveGeometriat(any(), any()) }
    }

    @Test
    fun `delete Geometria`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat-delete.json"
            .asJsonResource(HankeGeometriat::class.java)
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = Hanke(hankeId, hankeTunnus)
        val savedHankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        savedHankeGeometriat.hankeId = hankeId
        savedHankeGeometriat.version = 1

        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { hankeGeometriatService.saveGeometriat(hanke, any()) } returns savedHankeGeometriat
        mockMvc.perform(
            post("/hankkeet/$hankeTunnus/geometriat")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(hankeGeometriat.toJsonString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().string(savedHankeGeometriat.toJsonString()))

        verify { hankeGeometriatService.saveGeometriat(hanke, any()) }
    }

}
