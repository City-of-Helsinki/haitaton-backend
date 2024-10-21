package fi.hel.haitaton.hanke.paatos

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.DownloadNotFoundException
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [PaatosController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class PaatosControllerITest(
    @Autowired override val mockMvc: MockMvc,
    @Autowired private val paatosService: PaatosService,
    @Autowired private val authorizer: PaatosAuthorizer,
    @Autowired private val disclosureLogService: DisclosureLogService,
) : ControllerTest {
    private val id = UUID.fromString("34efbc8c-caae-4254-8170-98b009c5a866")

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(paatosService, authorizer)
    }

    @Nested
    inner class Download {
        private val url = "/paatokset/$id"

        private val hakemusId = 673L
        private val alluId = 51
        private val hakemustunnus = "KP2425421-2"
        val hakemus =
            HakemusFactory.create(
                id = hakemusId,
                alluid = alluId,
                alluStatus = ApplicationStatus.DECISION,
                applicationIdentifier = hakemustunnus,
                applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
            )
        private val paatos = PaatosFactory.createForHakemus(hakemus)

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is not authenticated`() {
            get(url).andExpect(status().isUnauthorized)

            verify { paatosService wasNot Called }
        }

        @Test
        fun `returns 404 when paatos doesn't exist`() {
            every { authorizer.authorizePaatosId(id, PermissionCode.VIEW.name) } throws
                PaatosNotFoundException(id)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI5001))

            verifySequence { authorizer.authorizePaatosId(id, PermissionCode.VIEW.name) }
        }

        @Test
        fun `returns 500 when paatos download fails`() {
            every { authorizer.authorizePaatosId(id, PermissionCode.VIEW.name) } returns true
            every { paatosService.findById(id) } returns paatos
            every { paatosService.downloadDecision(paatos) } throws
                DownloadNotFoundException(paatos.blobLocation, Container.PAATOKSET)

            get(url)
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI0002))

            verifySequence {
                authorizer.authorizePaatosId(id, PermissionCode.VIEW.name)
                paatosService.findById(id)
                paatosService.downloadDecision(paatos)
            }
        }

        @Test
        fun `returns bytes and correct headers`() {
            every { authorizer.authorizePaatosId(id, PermissionCode.VIEW.name) } returns true
            every { paatosService.findById(id) } returns paatos
            every { paatosService.downloadDecision(paatos) } returns
                Pair("$hakemustunnus-paatos.pdf", PDF_BYTES)

            get(url, MediaType.APPLICATION_PDF)
                .andExpect(status().isOk)
                .andExpect(
                    MockMvcResultMatchers.header()
                        .string("Content-Disposition", "inline; filename=KP2425421-2-paatos.pdf"))
                .andExpect(MockMvcResultMatchers.content().bytes(PDF_BYTES))

            verifySequence {
                authorizer.authorizePaatosId(id, PermissionCode.VIEW.name)
                paatosService.findById(id)
                paatosService.downloadDecision(paatos)
            }
        }

        @Test
        fun `writes access to audit logs`() {
            every { authorizer.authorizePaatosId(id, PermissionCode.VIEW.name) } returns true
            every { paatosService.findById(id) } returns paatos
            every { paatosService.downloadDecision(paatos) } returns
                Pair("$hakemustunnus-paatos.pdf", PDF_BYTES)

            get(url, MediaType.APPLICATION_PDF).andExpect(status().isOk)

            verifySequence {
                authorizer.authorizePaatosId(id, PermissionCode.VIEW.name)
                paatosService.findById(id)
                paatosService.downloadDecision(paatos)
                disclosureLogService.saveForPaatos(paatos.toMetadata(), USERNAME)
            }
        }
    }
}
