package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import java.util.UUID
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TaydennysController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TaydennysControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {
    @Autowired private lateinit var taydennysService: TaydennysService
    @Autowired private lateinit var hakemusAuthorizer: HakemusAuthorizer
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    private val hakemusId = 24050L

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hakemusAuthorizer, taydennysService, disclosureLogService)
    }

    @Nested
    inner class Create {
        private val url = "/hakemukset/$hakemusId/taydennys"

        private val taydennys =
            Taydennys(
                UUID.fromString("90b67df3-cd13-4dca-bd30-9dda424d1260"),
                TaydennyspyyntoFactory.DEFAULT_ID,
                HakemusFactory.createJohtoselvityshakemusData(name = "Täydennettävä hakemus"))

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unknown`() {
            post(url).andExpect(status().isUnauthorized).andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `returns 404 when application does not exist`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(hakemusId)

            post(url).andExpect(status().isNotFound)

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 409 when hakemus doesn't have an open taydennyspyynto`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.create(hakemusId, USERNAME) } throws
                NoTaydennyspyyntoException(hakemusId)

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns 409 when the hakemus is not in WAITING_INFORMATION status`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            val hakemus = HakemusFactory.create(hakemusId, alluStatus = ApplicationStatus.HANDLING)
            every { taydennysService.create(hakemusId, USERNAME) } throws
                HakemusInWrongStatusException(
                    hakemus,
                    ApplicationStatus.HANDLING,
                    listOf(ApplicationStatus.WAITING_INFORMATION))

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns the created taydennys and writes the access to disclosure logs`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.create(hakemusId, USERNAME) } returns taydennys

            val response: TaydennysResponse = post(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(TaydennysResponse::id)
                    .isEqualTo(UUID.fromString("90b67df3-cd13-4dca-bd30-9dda424d1260"))
                prop(TaydennysResponse::applicationData).all {
                    prop(HakemusDataResponse::name).isEqualTo("Täydennettävä hakemus")
                }
            }
            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.create(hakemusId, USERNAME)
                disclosureLogService.saveDisclosureLogsForTaydennys(response, USERNAME)
            }
        }
    }
}
