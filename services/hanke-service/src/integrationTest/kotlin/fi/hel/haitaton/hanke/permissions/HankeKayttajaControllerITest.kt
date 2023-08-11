package fi.hel.haitaton.hanke.permissions

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val USERNAME = "testUser"
private const val HANKE_TUNNUS = HankeFactory.defaultHankeTunnus

@WebMvcTest(HankeKayttajaController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME)
class HankeKayttajaControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hankeKayttajaService: HankeKayttajaService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hankeKayttajaService, hankeService, permissionService)
    }

    @Test
    fun `getHankeKayttajat when valid request returns users of given hanke and logs audit`() {
        val hanke = HankeFactory.create()
        val testData = HankeKayttajaFactory.generateHankeKayttajat()
        every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
        justRun { permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW) }
        every { hankeKayttajaService.getKayttajatByHankeId(hanke.id!!) } returns testData

        val response: HankeKayttajaResponse =
            getHankeKayttajat().andExpect(status().isOk).andReturnBody()

        assertThat(response.kayttajat).hasSize(3)
        with(response.kayttajat.first()) {
            assertThat(id).isNotNull()
            assertThat(nimi).isEqualTo("test name1")
            assertThat(sahkoposti).isEqualTo("email.1.address.com")
            assertThat(tunnistautunut).isEqualTo(false)
        }
        assertThat(response.kayttajat).hasSameElementsAs(testData)
        verifyOrder {
            hankeService.findHankeOrThrow(HANKE_TUNNUS)
            permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW)
            hankeKayttajaService.getKayttajatByHankeId(hanke.id!!)
            disclosureLogService.saveDisclosureLogsForHankeKayttajat(response.kayttajat, USERNAME)
        }
    }

    @Test
    fun `getHankeKayttajat when no permission for hanke returns not found`() {
        val hanke = HankeFactory.create()
        every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
        every { permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW) } throws
            HankeNotFoundException(HANKE_TUNNUS)

        getHankeKayttajat().andExpect(status().isNotFound)

        verifyOrder {
            hankeService.findHankeOrThrow(HANKE_TUNNUS)
            permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW)
        }
        verify { hankeKayttajaService wasNot Called }
    }

    @Test
    @WithAnonymousUser
    fun `getHankeKayttajat when unauthorized token returns 401 `() {
        getHankeKayttajat().andExpect(status().isUnauthorized)
    }

    private fun getHankeKayttajat(): ResultActions = get("/hankkeet/$HANKE_TUNNUS/kayttajat")
}
