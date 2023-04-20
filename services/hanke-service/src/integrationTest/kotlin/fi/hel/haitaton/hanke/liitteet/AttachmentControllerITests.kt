package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header

private const val USERNAME = "username"

@WebMvcTest(AttachmentsController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class AttachmentControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var attachmentService: AttachmentService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        confirmVerified(attachmentService)
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Get hanke metadata fails without permissions`() {
        val hankeTunnus = "123"
        val hankeId = 123
        val liiteId = UUID.randomUUID()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(hankeTunnus, liiteId))
        every { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
            .returns(false)

        get("/liitteet/${liiteId}").andExpect(MockMvcResultMatchers.status().`is`(404))

        verify { attachmentService.get(liiteId) }
        verify { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Get attachment metadata`() {
        val hankeTunnus = "123"
        val hankeId = 123
        val liiteId = UUID.randomUUID()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(hankeTunnus, liiteId, "file.pdf"))
        every { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
            .returns(true)

        get("/liitteet/${liiteId}").andExpect(MockMvcResultMatchers.status().`is`(200))

        verify { attachmentService.get(liiteId) }
        verify { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Get attachment data`() {
        val hankeTunnus = "123"
        val hankeId = 123
        val liiteId = UUID.randomUUID()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(hankeTunnus, liiteId, "file.pdf"))
        every { attachmentService.getContent(liiteId) }.returns(byteArrayOf(1, 2, 3, 4))
        every { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
            .returns(true)

        get("/liitteet/${liiteId}/content")
            .andExpect(MockMvcResultMatchers.status().`is`(200))
            .andExpect(
                header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=file.pdf")
            )
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3, 4)))

        verify { attachmentService.get(liiteId) }
        verify { attachmentService.getContent(liiteId) }
        verify { permissionService.hasPermission(hankeId, USERNAME, PermissionCode.VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Attachment can be removed`() {
        val hankeTunnus = "123"
        val hankeId = 123
        val userId = currentUserId()
        val liiteId = UUID.randomUUID()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(hankeTunnus, liiteId))
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
            .returns(true)
        every { attachmentService.removeAttachment(liiteId) } just runs

        delete("/liitteet/${liiteId}").andExpect(MockMvcResultMatchers.status().`is`(200))

        verify { attachmentService.get(liiteId) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
        verify { attachmentService.removeAttachment(liiteId) }
    }
}
