package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeController
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeWithApplications
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.permissions.Role
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
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
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HankeControllerSecurityTests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    private val testHankeTunnus = "HAI21-TEST-1"

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(permissionService, disclosureLogService, hankeService)
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `GET hankkeet with authenticated user with or without any role returns ok (200)`() {
        val hankeIds = listOf(123, 444)
        every { hankeService.loadHankkeetByIds(hankeIds) } returns
            listOf(
                HankeFactory.create(id = 123, hankeTunnus = testHankeTunnus),
                HankeFactory.create(id = 444, hankeTunnus = "HAI-TEST-2")
            )
        every { permissionService.getAllowedHankeIds("test7358", PermissionCode.VIEW) } returns
            (listOf(123, 444))

        get("/hankkeet").andExpect(status().isOk)

        verifySequence {
            permissionService.getAllowedHankeIds("test7358", PermissionCode.VIEW)
            hankeService.loadHankkeetByIds(hankeIds)
            disclosureLogService.saveDisclosureLogsForHankkeet(any(), "test7358")
        }
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `GET hanke by tunnus with authenticated user with or without any role returns ok (200)`() {
        every { hankeService.loadHanke(testHankeTunnus) } returns
            HankeFactory.create(id = 123, hankeTunnus = testHankeTunnus)
        every { permissionService.hasPermission(123, "test7358", PermissionCode.VIEW) } returns true

        get("/hankkeet/$testHankeTunnus").andExpect(status().isOk)

        verifySequence {
            hankeService.loadHanke(testHankeTunnus)
            permissionService.hasPermission(123, "test7358", PermissionCode.VIEW)
            disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358")
        }
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `GET hanke hakemukset with authenticated user with or without any role returns ok (200)`() {
        every { hankeService.getHankeWithApplications(any()) } returns
            HankeWithApplications(
                HankeFactory.create(id = 123, hankeTunnus = testHankeTunnus),
                listOf()
            )
        every { permissionService.hasPermission(123, "test7358", PermissionCode.VIEW) } returns true

        get("/hankkeet/$testHankeTunnus/hakemukset").andExpect(status().isOk)

        verifySequence {
            hankeService.getHankeWithApplications(any())
            permissionService.hasPermission(123, "test7358", PermissionCode.VIEW)
            disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358")
        }
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `POST hankkeet with authenticated user with or without any role returns ok (200)`() {
        val hanke = getTestHanke(null, null)
        every { hankeService.createHanke(any()) } returns
            hanke.copy(id = 12, hankeTunnus = testHankeTunnus)
        justRun { permissionService.setPermission(12, "test7358", Role.KAIKKI_OIKEUDET) }

        post("/hankkeet", hanke).andExpect(status().isOk)

        verifySequence {
            hankeService.createHanke(any())
            permissionService.setPermission(12, "test7358", Role.KAIKKI_OIKEUDET)
            disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358")
        }
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `PUT hankkeet with authenticated user with or without any role returns ok (200)`() {
        val hanke = getTestHanke(123, testHankeTunnus)
        every { hankeService.loadHanke(any()) } returns hanke
        every {
            permissionService.hasPermission(hanke.id!!, "test7358", PermissionCode.EDIT)
        } returns true
        every { hankeService.updateHanke(any()) } returns hanke.copy(modifiedBy = "test7358")

        put("/hankkeet/$testHankeTunnus", hanke).andExpect(status().isOk)

        verifySequence {
            hankeService.loadHanke(testHankeTunnus)
            permissionService.hasPermission(hanke.id!!, "test7358", PermissionCode.EDIT)
            hankeService.updateHanke(any())
            disclosureLogService.saveDisclosureLogsForHanke(any(), "test7358")
        }
    }

    @Test
    fun `GET hankkeet without authenticated user returns unauthorized (401) `() {
        get("/hankkeet")
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
    }

    @Test
    fun `GET hanke by tunnus without authenticated user returns unauthorized (401) `() {
        get("/hankkeet/$testHankeTunnus")
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
    }

    @Test
    fun `GET hanke hakemukset without authenticated user returns unauthorized (401) `() {
        get("/hankkeet/$testHankeTunnus/hakemukset")
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
    }

    @Test
    fun `POST hankkeet without authenticated user returns unauthorized (401) `() {
        post("/hankkeet", getTestHanke(null, null))
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
    }

    @Test
    fun `PUT hankkeet without authenticated user returns unauthorized (401) `() {
        put("/hankkeet/$testHankeTunnus", getTestHanke(12, testHankeTunnus))
            .andExpect(unauthenticated())
            .andExpect(status().isUnauthorized)
            .andExpectHankeError(HankeError.HAI0001)
    }

    private fun getTestHanke(id: Int?, tunnus: String?): Hanke =
        Hanke(
            id,
            tunnus,
            true,
            "Testihanke",
            "Testihankkeen kuvaus",
            Vaihe.OHJELMOINTI,
            null,
            1,
            "test7358",
            getCurrentTimeUTC(),
            null,
            null,
            HankeStatus.DRAFT
        )

    private fun ResultActions.andExpectHankeError(hankeError: HankeError): ResultActions {
        return andExpect(MockMvcResultMatchers.jsonPath("$.errorCode").value(hankeError.errorCode))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.errorMessage").value(hankeError.errorMessage)
            )
    }
}
