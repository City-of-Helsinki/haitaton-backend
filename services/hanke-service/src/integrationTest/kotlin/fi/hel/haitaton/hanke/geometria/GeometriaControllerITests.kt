package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.HankeFactory
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(GeometriaController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
internal class GeometriaControllerITests(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {

    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var geometriatService: GeometriatService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hankeService, geometriatService)
    }

    @Test
    fun `get Geometria OK`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = HankeFactory.create(id = hankeId, hankeTunnus = hankeTunnus)
        val geometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { geometriatService.loadGeometriat(hanke) } returns geometriat

        get("/hankkeet/$hankeTunnus/geometriat").andExpect(status().isOk)

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { geometriatService.loadGeometriat(hanke) }
    }

    @Test
    fun `get Geometria for missing Hanke`() {
        val hankeTunnus = "1234567"
        every { hankeService.loadHanke(hankeTunnus) } returns null

        get("/hankkeet/$hankeTunnus/geometriat")
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1001"))

        verify { hankeService.loadHanke(hankeTunnus) }
        verify(exactly = 0) { geometriatService.loadGeometriat(any()) }
    }

    @Test
    fun `get Geometria for missing geometry`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = HankeFactory.create(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { geometriatService.loadGeometriat(hanke) } returns null

        get("/hankkeet/$hankeTunnus/geometriat")
            .andExpect(status().isNotFound)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI1015"))

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { geometriatService.loadGeometriat(hanke) }
    }

    @Test
    fun `get Geometria with internal error`() {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = HankeFactory.create(id = hankeId, hankeTunnus = hankeTunnus)
        every { hankeService.loadHanke(hankeTunnus) } returns hanke
        every { geometriatService.loadGeometriat(hanke) } throws RuntimeException()

        get("/hankkeet/$hankeTunnus/geometriat")
            .andExpect(status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value("HAI0002"))

        verify { hankeService.loadHanke(hankeTunnus) }
        verify { geometriatService.loadGeometriat(hanke) }
    }
}
