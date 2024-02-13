package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.application.ApplicationAuthorizer
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.permissions.PermissionCode
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

private const val USERNAME = "testUser"
private const val HANKE_TUNNUS = "HAI-1234"
private const val BASE_URL = "/hakemukset"

@WebMvcTest(
    controllers = [HakemusController::class],
    properties = ["haitaton.features.user-management=true"]
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HakemusControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hakemusService: HakemusService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var authorizer: ApplicationAuthorizer

    private val id = 1234L

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hakemusService, hankeService, authorizer)
    }

    @Nested
    inner class GetById {
        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            get("$BASE_URL/$id").andExpect(MockMvcResultMatchers.status().isUnauthorized)

            verify { hakemusService wasNot Called }
        }

        @Test
        fun `when application does not exist should return 404`() {
            every { authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name) } throws
                ApplicationNotFoundException(id)

            get("$BASE_URL/$id").andExpect(MockMvcResultMatchers.status().isNotFound)

            verify { authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name) }
        }

        @Test
        fun `when application exists should return it`() {
            every { authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.hakemusResponse(id) } returns
                HakemusFactory.createHakemusResponse(id, HANKE_TUNNUS)

            get("$BASE_URL/$id")
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(
                    MockMvcResultMatchers.jsonPath("$.applicationType").value("CABLE_REPORT")
                )
                .andExpect(
                    MockMvcResultMatchers.jsonPath("$.applicationData.applicationType")
                        .value("CABLE_REPORT")
                )

            verify {
                authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name)
                hakemusService.hakemusResponse(id)
            }
        }
    }
}
