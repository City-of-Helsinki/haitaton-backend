package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
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

@WebMvcTest(controllers = [MuutosilmoitusController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class MuutosilmoitusControllerITest(
    @Autowired override val mockMvc: MockMvc,
    @Autowired private var muutosilmoitusService: MuutosilmoitusService,
    @Autowired private var hakemusAuthorizer: HakemusAuthorizer,
    @Autowired private var disclosureLogService: DisclosureLogService,
) : ControllerTest {
    private val hakemusId = 567813L
    private val id = UUID.fromString("c78755ad-4cf7-46ad-9f60-0356da6e41c6")

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(muutosilmoitusService)
    }

    @Nested
    inner class Create {
        private val url = "/hakemukset/$hakemusId/muutosilmoitus"

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unknown`() {
            post(url).andExpect(status().isUnauthorized).andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `returns 404 when application does not exist`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } throws HakemusNotFoundException(hakemusId)

            post(url).andExpect(status().isNotFound)

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            }
        }

        @Test
        fun `returns 409 when the hakemus is not in an allowed status`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val hakemus = HakemusFactory.create(hakemusId, alluStatus = ApplicationStatus.HANDLING)
            every { muutosilmoitusService.create(hakemusId, USERNAME) } throws
                HakemusInWrongStatusException(
                    hakemus,
                    ApplicationStatus.HANDLING,
                    listOf(ApplicationStatus.DECISION, ApplicationStatus.OPERATIONAL_CONDITION),
                )

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                muutosilmoitusService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns 400 when the hakemus is a johtoselvityshakemus`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val hakemus = HakemusFactory.create(hakemusId, alluStatus = ApplicationStatus.HANDLING)
            every { muutosilmoitusService.create(hakemusId, USERNAME) } throws
                WrongHakemusTypeException(
                    hakemus,
                    ApplicationType.CABLE_REPORT,
                    listOf(ApplicationType.EXCAVATION_NOTIFICATION),
                )

            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI2002))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                muutosilmoitusService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns the created muutosilmoitus and writes the access to disclosure logs`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val muutosilmoitus =
                Muutosilmoitus(
                    id,
                    hakemusId,
                    sent = null,
                    HakemusFactory.createKaivuilmoitusData(name = "Muutettu hakemus"),
                )
            every { muutosilmoitusService.create(hakemusId, USERNAME) } returns muutosilmoitus

            val response: MuutosilmoitusResponse =
                post(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(MuutosilmoitusResponse::id).isEqualTo(id)
                prop(MuutosilmoitusResponse::sent).isNull()
                prop(MuutosilmoitusResponse::applicationData).all {
                    prop(HakemusDataResponse::name).isEqualTo("Muutettu hakemus")
                }
            }
            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                muutosilmoitusService.create(hakemusId, USERNAME)
                disclosureLogService.saveForMuutosilmoitus(response, USERNAME)
            }
        }
    }
}
