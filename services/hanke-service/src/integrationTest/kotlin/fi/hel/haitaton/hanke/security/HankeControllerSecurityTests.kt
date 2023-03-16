package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeController
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.every
import io.mockk.justRun
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests to ensure HankeController endpoints have correct authentication and authorization
 * restrictions and handling.
 *
 * The actual activities in each test are not important, as long as they cause the relevant method
 * to be called. "Perform" function implementations have been adapted from HankeControllerITests. As
 * long as the mocked operation goes through and returns something "ok" (when authenticated
 * correctly), it will be usable as an authentication test's operation.
 *
 * TODO Note, HankeService's method-based security is to be tested separately, (in own test class),
 * once it gets any activated. For now, testing the Controller is enough as the service's finer
 * things are not implemented.
 */
@WebMvcTest(HankeController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HankeControllerSecurityTests(@Autowired val mockMvc: MockMvc) {

    @Autowired private lateinit var hankeService: HankeService

    @Autowired lateinit var permissionService: PermissionService

    @Autowired private lateinit var geometriatService: GeometriatService

    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    private val testHankeTunnus = "HAI21-TEST-1"

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `status ok with authenticated user with or without any role`() {
        performGetHankkeet().andExpect(status().isOk)
        performGetHankeHakemukset().andExpect(status().isOk)
        performPostHankkeet().andExpect(status().isOk)
        performPutHankkeetTunnus().andExpect(status().isOk)
        performGetHankeByTunnus().andExpect(status().isOk)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status unauthorized (401) without authenticated user`() {
        performGetHankkeet()
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
        performGetHankeHakemukset()
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
        performPostHankkeet()
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
        performPutHankkeetTunnus()
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
        performGetHankeByTunnus()
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
    }

    // --------- GET /hankkeet/ --------------

    private fun performGetHankkeet(): ResultActions {
        val hankeIds = listOf(123, 444)
        every { hankeService.loadHankkeetByIds(hankeIds) }
            .returns(
                listOf(
                    HankeFactory.create(id = 123, hankeTunnus = testHankeTunnus),
                    HankeFactory.create(id = 444, hankeTunnus = "HAI-TEST-2")
                )
            )
        every { permissionService.getAllowedHankeIds("test7358", PermissionCode.VIEW) }
            .returns(listOf(123, 444))
        justRun { disclosureLogService.saveDisclosureLogsForHankkeet(any(), "test7358") }

        return mockMvc.perform(get("/hankkeet").accept(MediaType.APPLICATION_JSON))
    }

    // --------- POST /hankkeet/ --------------

    private fun performPostHankkeet(): ResultActions {
        val hanke = getTestHanke(12, null)
        val content = hanke.toJsonString()

        every { hankeService.createHanke(any()) }.returns(hanke)
        justRun { permissionService.setPermission(12, "test7358", Role.KAIKKI_OIKEUDET) }
        every { geometriatService.loadGeometriat(any()) }.returns(null)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358") }

        return mockMvc.perform(
            post("/hankkeet")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(content)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
    }

    // --------- PUT /hankkeet/{hankeTunnus} --------------

    private fun performPutHankkeetTunnus(): ResultActions {
        val hanke = getTestHanke(123, testHankeTunnus)
        val content = hanke.toJsonString()

        every { hankeService.getHankeId(any()) }.returns(hanke.id)
        every { permissionService.hasPermission(hanke.id!!, "test7358", PermissionCode.EDIT) }
            .returns(true)
        every { hankeService.updateHanke(any()) }.returns(hanke.copy(modifiedBy = "test7358"))
        justRun { disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358") }

        return mockMvc.perform(
            put("/hankkeet/$testHankeTunnus")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(content)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
    }

    // --------- GET /hankkeet/{hankeTunnus} --------------

    private fun performGetHankeByTunnus(): ResultActions {
        every { hankeService.loadHanke(any()) }
            .returns(HankeFactory.create(id = 123, hankeTunnus = "HAI-TEST-1"))
        every { permissionService.hasPermission(123, "test7358", PermissionCode.VIEW) }
            .returns(true)
        justRun { disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358") }

        return mockMvc.perform(get("/hankkeet/$testHankeTunnus").accept(MediaType.APPLICATION_JSON))
    }

    // --------- GET /hankkeet/{hankeTunnus}/hakemukset --------------

    private fun performGetHankeHakemukset(): ResultActions {
        every { hankeService.getHankeHakemuksetPair(any()) } returns
            Pair(HankeFactory.create(id = 123, hankeTunnus = "HAI-TEST-1"), listOf())
        every { permissionService.hasPermission(123, "test7358", PermissionCode.VIEW) } returns true
        justRun { disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358") }

        return mockMvc.perform(
            get("/hankkeet/$testHankeTunnus/hakemukset").accept(MediaType.APPLICATION_JSON)
        )
    }

    // ===================== HELPERS ========================

    private fun getDatetimeAlku(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC).truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getDatetimeLoppu(): ZonedDateTime {
        val year = getCurrentTimeUTC().year + 1
        return ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC).truncatedTo(ChronoUnit.MILLIS)
    }

    private fun getTestHanke(id: Int?, tunnus: String?): Hanke {
        return Hanke(
            id,
            tunnus,
            true,
            "Testihanke",
            "Testihankkeen kuvaus",
            getDatetimeAlku(),
            getDatetimeLoppu(),
            Vaihe.OHJELMOINTI,
            null,
            1,
            "test7358",
            getCurrentTimeUTC(),
            null,
            null,
            HankeStatus.DRAFT
        )
    }

    private fun ResultActions.andExpectHankeError(hankeError: HankeError): ResultActions {
        return andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value(hankeError.errorCode))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.errorMessage").value(hankeError.errorMessage)
            )
    }
}
