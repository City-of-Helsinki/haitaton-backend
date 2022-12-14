package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.GeometriaController
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests to ensure HankeGeometriaController endpoints have correct authentication and authorization
 * restrictions and handling.
 *
 * Currently the same rules as for HankeController.
 *
 * The actual activities in each test are not important, as long as they cause the relevant method
 * to be called. "Perform" function implementations have been adapted from
 * GeometriatControllerITests. As long as the mocked operation goes through and returns something
 * "ok" (when authenticated correctly), it will be usable as an authentication test's operation.
 */
@WebMvcTest(GeometriaController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeometriaControllerSecurityTests(@Autowired val mockMvc: MockMvc) {

    @Autowired private lateinit var hankeService: HankeService

    @Autowired private lateinit var geometriatService: GeometriatService

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `status ok with authenticated user with or without any role`() {
        performGetHankkeetTunnusGeometriat().andExpect(status().isOk)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status unauthorized (401) without authenticated user`() {
        performGetHankkeetTunnusGeometriat()
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
    }

    // ---------- GET /hankkeet/{hankeTunnus}/geometriat ----------

    private fun performGetHankkeetTunnusGeometriat(): ResultActions {
        val hankeTunnus = "1234567"
        val hankeId = 1
        val hanke = Hanke(hankeId, hankeTunnus)
        val geometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )

        every { hankeService.loadHanke(any()) } returns hanke
        every { geometriatService.loadGeometriat(any()) } returns geometriat

        return mockMvc.perform(
            get("/hankkeet/$hankeTunnus/geometriat").accept(MediaType.APPLICATION_JSON)
        )
    }
}
