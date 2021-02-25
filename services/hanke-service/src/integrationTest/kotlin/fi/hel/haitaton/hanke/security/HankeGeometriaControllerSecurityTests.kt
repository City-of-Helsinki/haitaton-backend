package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.HankeGeometriaController
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests to ensure HankeGeometriaController endpoints have correct authentication
 * and authorization restrictions and handling.
 *
 * Currently the same rules as for HankeController.
 *
 * The actual activities in each test are not important, as long as they cause
 * the relevant method to be called.
 * "Perform" function implementations have been adapted from HankeGeometriatControllerITests.
 * As long as the mocked operation goes through and returns something "ok" (when
 * authenticated correctly), it will be usable as an authentication test's operation.
 */
@WebMvcTest(HankeGeometriaController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HankeGeometriaControllerSecurityTests(@Autowired val mockMvc: MockMvc) {

    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

    @Test
    @WithMockUser(username = "test7358", roles = ["haitaton-user"])
    fun `status ok with authenticated user with correct role`() {
        performPostHankkeetTunnusGeometriat().andExpect(status().isOk)
        performGetHankkeetTunnusGeometriat().andExpect(status().isOk)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status unauthorized (401) without authenticated user`() {
        performPostHankkeetTunnusGeometriat()
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized)
        performGetHankkeetTunnusGeometriat()
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "test7358", roles = ["bad role"])
    fun `status forbidden (403) with authenticated user with bad role`() {
        performPostHankkeetTunnusGeometriat().andExpect(status().isForbidden)
        performGetHankkeetTunnusGeometriat().andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `status forbidden (403) with authenticated user without roles`() {
        performPostHankkeetTunnusGeometriat().andExpect(status().isForbidden)
        performGetHankkeetTunnusGeometriat().andExpect(status().isForbidden)
    }

    // ---------- POST /hankkeet/{hankeTunnus}/geometriat ----------

    private fun performPostHankkeetTunnusGeometriat(): ResultActions {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.hankeId = null
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        val hankeTunnus = "1234567"
        val hankeId = 1
        val savedHankeGeometriat =
                "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        savedHankeGeometriat.hankeId = hankeId
        savedHankeGeometriat.version = 1

        every { hankeGeometriatService.saveGeometriat(hankeTunnus, any()) } returns savedHankeGeometriat

        return mockMvc.perform(
                post("/hankkeet/$hankeTunnus/geometriat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .content(hankeGeometriat.toJsonString())
                        .accept(MediaType.APPLICATION_JSON)
        )
    }

    // ---------- GET /hankkeet/{hankeTunnus}/geometriat ----------

    private fun performGetHankkeetTunnusGeometriat(): ResultActions {
        val hankeTunnus = "1234567"
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)

        every { hankeGeometriatService.loadGeometriat(hankeTunnus) } returns hankeGeometriat

        return mockMvc.perform(
                get("/hankkeet/$hankeTunnus/geometriat")
                        .accept(MediaType.APPLICATION_JSON)
        )
    }

}
