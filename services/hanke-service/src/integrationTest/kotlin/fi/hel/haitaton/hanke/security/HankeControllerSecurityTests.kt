package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeController
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import fi.hel.haitaton.hanke.logging.YhteystietoLoggingService
import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionProfiles
import fi.hel.haitaton.hanke.permissions.PermissionService
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

    @Autowired private lateinit var hankeGeometriatService: HankeGeometriatService

    @Autowired private lateinit var yhteystietoLoggingService: YhteystietoLoggingService

    private val testHankeTunnus = "HAI21-TEST-1"

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `status ok with authenticated user with or without any role`() {
        performGetHankkeet().andExpect(status().isOk)
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
            .returns(listOf(Hanke(123, testHankeTunnus), Hanke(444, "HAI-TEST-2")))
        every { permissionService.getPermissionsByUserId("test7358") }
            .returns(
                listOf(
                    Permission(1, "test7358", 123, PermissionProfiles.HANKE_OWNER_PERMISSIONS),
                    Permission(1, "test7358", 444, listOf(PermissionCode.VIEW))
                )
            )
        justRun { yhteystietoLoggingService.saveDisclosureLogsForUser(any(), "test7358") }

        return mockMvc.perform(get("/hankkeet").accept(MediaType.APPLICATION_JSON))
    }

    // --------- POST /hankkeet/ --------------

    private fun performPostHankkeet(): ResultActions {
        val hanke = getTestHanke(12, null)
        val content = hanke.toJsonString()

        every { hankeService.createHanke(any()) }.returns(hanke)
        every {
                permissionService.setPermission(
                    12,
                    "test7358",
                    PermissionProfiles.HANKE_OWNER_PERMISSIONS
                )
            }
            .returns(Permission(1, "test7358", 12, PermissionProfiles.HANKE_OWNER_PERMISSIONS))
        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)
        justRun { yhteystietoLoggingService.saveDisclosureLogForUser(any(), "test7358") }

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

        every { hankeService.updateHanke(any()) }.returns(hanke.copy(modifiedBy = "test7358"))
        justRun { yhteystietoLoggingService.saveDisclosureLogForUser(any(), "test7358") }

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
        every { hankeService.loadHanke(any()) }.returns(Hanke(123, "HAI-TEST-1"))
        every { permissionService.getPermissionByHankeIdAndUserId(123, "test7358") }
            .returns(Permission(1, "test7358", 123, PermissionProfiles.HANKE_OWNER_PERMISSIONS))
        justRun { yhteystietoLoggingService.saveDisclosureLogForUser(any(), "test7358") }

        return mockMvc.perform(get("/hankkeet/$testHankeTunnus").accept(MediaType.APPLICATION_JSON))
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
            SaveType.DRAFT
        )
    }

    private fun ResultActions.andExpectHankeError(hankeError: HankeError): ResultActions {
        return andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value(hankeError.errorCode))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.errorMessage").value(hankeError.errorMessage)
            )
    }
}
