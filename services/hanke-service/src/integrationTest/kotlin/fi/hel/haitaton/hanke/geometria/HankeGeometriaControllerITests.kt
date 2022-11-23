package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.domain.Hanke
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HankeGeometriaController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
internal class HankeGeometriaControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var hankeGeometriatService: HankeGeometriatService

    @Test
    fun `get Geometria OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = Hanke(hankeId, hankeTunnus)

        val hankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                HankeGeometriat::class.java
            )
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { hankeGeometriatService.loadGeometriat(hanke) } returns hankeGeometriat
        mockMvc
            .perform(get("/hankkeet/$hankeTunnus/geometriat").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
        //
        // .andExpect(MockMvcResultMatchers.jsonPath("$.hankeId").value(hankeGeometriat.hankeId!!))
        verify { hankeGeometriatService.loadGeometriat(hanke) }
    }

    @Test
    fun `get Geometria for missing Hanke`() {
        val hankeTunnus = "1234567"
        every { hankeService.loadHanke(hankeTunnus) } returns null
        mockMvc
            .perform(get("/hankkeet/$hankeTunnus/geometriat").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))
        verify { hankeService.loadHanke(hankeTunnus) }
        verify(exactly = 0) { hankeGeometriatService.loadGeometriat(any()) }
    }

    @Test
    fun `get Geometria for missing geometry`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = Hanke(hankeId, hankeTunnus)
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { hankeGeometriatService.loadGeometriat(hanke) } returns null
        mockMvc
            .perform(get("/hankkeet/$hankeTunnus/geometriat").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1015"))
        verify { hankeGeometriatService.loadGeometriat(hanke) }
    }

    @Test
    fun `get Geometria with internal error`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = Hanke(hankeId, hankeTunnus)
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { hankeGeometriatService.loadGeometriat(hanke) } throws RuntimeException()
        mockMvc
            .perform(get("/hankkeet/$hankeTunnus/geometriat").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI0002"))
        verify { hankeGeometriatService.loadGeometriat(hanke) }
    }
}
