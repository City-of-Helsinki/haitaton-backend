package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeController
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.getCurrentTimeUTC
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit


/**
 * Tests to ensure HankeController endpoints have correct authentication and
 * authorization restrictions and handling.
 *
 * The actual activities in each test are not important, as long as they cause
 * the relevant method to be called.
 * "Perform" function implementations have been adapted from HankeControllerITests.
 * As long as the mocked operation goes through and returns something "ok" (when
 * authenticated correctly), it will be usable as an authentication test's operation.
 *
 * TODO
 * Note, HankeService's method-based security is to be tested separately, (in own test class),
 * once it gets any activated. For now, testing the Controller is enough as the service's
 * finer things are not implemented.
 */
@WebMvcTest(HankeController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HankeControllerSecurityTests(@Autowired val mockMvc: MockMvc) {

    @Autowired
    private lateinit var hankeService: HankeService

    private val testHankeTunnus = "HAI21-TEST-1"


    @Test
    @WithMockUser(username = "test7358", roles = ["haitaton-user"])
    fun `status ok with authenticated user with correct role`() {
        performGetHankkeet().andExpect(status().isOk)
        performPostHankkeet().andExpect(status().isOk)
        performPutHankkeetTunnus().andExpect(status().isOk)
        performGetHankkeetTunnus().andExpect(status().isOk)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status unauthorized (401) without authenticated user`() {
        performGetHankkeet()
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized)
        performPostHankkeet()
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized)
        performPutHankkeetTunnus()
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized)
        performGetHankkeetTunnus()
                .andExpect(unauthenticated())
                .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "test7358", roles = ["bad role"])
    fun `status forbidden (403) with authenticated user with bad role`() {
        performGetHankkeet().andExpect(status().isForbidden)
        performPostHankkeet().andExpect(status().isForbidden)
        performPutHankkeetTunnus().andExpect(status().isForbidden)
        performGetHankkeetTunnus().andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `status forbidden (403) with authenticated user without roles`() {
        performGetHankkeet().andExpect(status().isForbidden)
        performPostHankkeet().andExpect(status().isForbidden)
        performPutHankkeetTunnus().andExpect(status().isForbidden)
        performGetHankkeetTunnus().andExpect(status().isForbidden)
    }

    // --------- GET /hankkeet/ --------------

    private fun performGetHankkeet(): ResultActions {
        val criteria = HankeSearch()
        every { hankeService.loadAllHanke(criteria) }.returns(
                listOf(Hanke(123, testHankeTunnus),
                        Hanke(444, "HAI-TEST-2")))

        return mockMvc.perform(get("/hankkeet")
                .accept(MediaType.APPLICATION_JSON))
    }

    // --------- POST /hankkeet/ --------------

    private fun performPostHankkeet(): ResultActions {
        val hanke = getTestHanke(null, null)
        val content = hanke.toJsonString()

        every { hankeService.createHanke(any()) }.returns(hanke)

        return mockMvc.perform(post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .accept(MediaType.APPLICATION_JSON))
    }

    // --------- PUT /hankkeet/{hankeTunnus} --------------

    private fun performPutHankkeetTunnus(): ResultActions {
        val hanke = getTestHanke(123, testHankeTunnus)
        val content = hanke.toJsonString()

        every { hankeService.updateHanke(any()) }.returns(hanke)

        return mockMvc.perform(put("/hankkeet/$testHankeTunnus")
                .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(content)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .accept(MediaType.APPLICATION_JSON))
    }

    // --------- GET /hankkeet/{hankeTunnus} --------------

    private fun performGetHankkeetTunnus(): ResultActions {
        every { hankeService.loadHanke(any()) }
                .returns(Hanke(123, "HAI-TEST-1"))

        return mockMvc.perform(get("/hankkeet/$testHankeTunnus")
                .accept(MediaType.APPLICATION_JSON))
    }


    // ===================== HELPERS ========================

    private fun getDatetimeAlku(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getDatetimeLoppu(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getTestHanke(id: Int?, tunnus: String?): Hanke {
        return Hanke(id, tunnus,
                true, "Testihanke", "Testihankkeen kuvaus",
                getDatetimeAlku(), getDatetimeLoppu(), Vaihe.OHJELMOINTI, null,
                1, "Risto", getCurrentTimeUTC(), null, null,
                SaveType.DRAFT)
    }

}
