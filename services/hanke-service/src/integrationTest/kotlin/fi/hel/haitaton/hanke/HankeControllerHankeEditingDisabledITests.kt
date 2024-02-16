package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.PermissionCode
import io.mockk.every
import io.mockk.justRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

private const val USERNAME = "test"
private const val HANKE_TUNNUS = HankeFactory.defaultHankeTunnus
private const val BASE_URL = "/hankkeet"

@WebMvcTest(HankeController::class, properties = ["haitaton.features.hanke-editing=false"])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeControllerHankeEditingDisabledITests(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {

    @Autowired lateinit var hankeService: HankeService
    @Autowired lateinit var authorizer: HankeAuthorizer

    @Test
    fun `post hanke when hanke editing is disabled should return 404`() {
        post(BASE_URL, HankeFactory.createRequest())
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(hankeError(HankeError.HAI0004))
    }

    @Test
    fun `put hanke when hanke editing is disabled should return 404`() {
        put("$BASE_URL/$HANKE_TUNNUS", HankeFactory.create())
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(hankeError(HankeError.HAI0004))
    }

    @Test
    fun `delete hanke works even if hanke editing is disabled`() {
        every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.DELETE.name) } returns
            true
        justRun { hankeService.deleteHanke(HANKE_TUNNUS, USERNAME) }

        delete("$BASE_URL/$HANKE_TUNNUS").andExpect(MockMvcResultMatchers.status().isNoContent)
    }
}
