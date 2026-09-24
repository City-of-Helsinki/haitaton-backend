package fi.hel.haitaton.hanke.verifiedname

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.VerifiedNameFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.security.JwtClaims
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import io.mockk.verifyAll
import java.net.SocketTimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

private const val URL = "/verified-name"
private const val LEGACY_URL = "/profiili/verified-name"

@WebMvcTest(controllers = [VerifiedNameController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class VerifiedNameControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var verifiedNameService: VerifiedNameService
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
    }

    @Nested
    inner class VerifiedName {
        @Test
        @WithAnonymousUser
        fun `Without user ID returns 401`() {
            get(URL).andExpect(MockMvcResultMatchers.status().isUnauthorized)

            verify { verifiedNameService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns 404 when the service throws expected exception`() {
            every { verifiedNameService.getVerifiedName(any()) } throws
                VerifiedNameNotFound("Because of reasons.")

            get(URL).andExpect(MockMvcResultMatchers.status().isNotFound)

            verifyAll { verifiedNameService.getVerifiedName(any()) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns 404 when name is not found from AD-token`() {
            every { verifiedNameService.getVerifiedName(any()) } throws
                NameClaimNotFound(JwtClaims.FAMILY_NAME)

            get(URL).andExpect(MockMvcResultMatchers.status().isNotFound)

            verifyAll { verifiedNameService.getVerifiedName(any()) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns 500 when the service throws unexpected exception`() {
            every { verifiedNameService.getVerifiedName(any()) } throws SocketTimeoutException()

            get(URL).andExpect(MockMvcResultMatchers.status().isInternalServerError)

            verifyAll { verifiedNameService.getVerifiedName(any()) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns verified names`() {
            every { verifiedNameService.getVerifiedName(any()) } returns
                VerifiedNameFactory.DEFAULT_NAMES
            every { disclosureLogService.saveForVerifiedName(any(), any()) } just Runs

            val names: Names =
                get(URL).andExpect(MockMvcResultMatchers.status().isOk).andReturnBody()

            assertThat(names).all {
                prop(Names::firstName).isEqualTo(VerifiedNameFactory.DEFAULT_FIRST_NAME)
                prop(Names::lastName).isEqualTo(VerifiedNameFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(VerifiedNameFactory.DEFAULT_GIVEN_NAME)
            }
            verifyAll {
                verifiedNameService.getVerifiedName(any())
                disclosureLogService.saveForVerifiedName(any(), any())
            }
        }

        @Test
        fun `the legacy profiili path still works`() {
            every { verifiedNameService.getVerifiedName(any()) } returns
                VerifiedNameFactory.DEFAULT_NAMES
            every { disclosureLogService.saveForVerifiedName(any(), any()) } just Runs

            val names: Names =
                get(LEGACY_URL).andExpect(MockMvcResultMatchers.status().isOk).andReturnBody()

            assertThat(names).all {
                prop(Names::firstName).isEqualTo(VerifiedNameFactory.DEFAULT_FIRST_NAME)
                prop(Names::lastName).isEqualTo(VerifiedNameFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(VerifiedNameFactory.DEFAULT_GIVEN_NAME)
            }
            verifyAll {
                verifiedNameService.getVerifiedName(any())
                disclosureLogService.saveForVerifiedName(any(), any())
            }
        }
    }
}
