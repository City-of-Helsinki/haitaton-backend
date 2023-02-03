package fi.hel.haitaton.hanke.liitteet

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.*
import java.util.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val username = "username"

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

    fun send(tunnus: String, file: MockMultipartFile): ResultActions {
        return mockMvc.perform(
            MockMvcRequestBuilders.multipart("/liitteet/hanke/${tunnus}")
                .file(file)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        )
    }

    @Test
    fun `Uploading without a session should fail`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        send("HAI-123", file).andExpect(status().`is`(401))
    }

    @Test
    @WithMockUser(username)
    fun `Uploading without hankeTunnus should fail`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        val hankeTunnus = "123"

        every { hankeService.getHankeId(hankeTunnus) }.returns(null)

        send(hankeTunnus, file).andExpect(status().`is`(404))

        verify { hankeService.getHankeId(hankeTunnus) }
    }

    @Test
    @WithMockUser(username)
    fun `Upload should fail when no rights for hanke`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        val hankeTunnus = "HAI-123"
        val hankeId = 123
        val userId = currentUserId()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
            .returns(false)

        send(hankeTunnus, file).andExpect(status().`is`(404))

        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
    }

    @Test
    @WithMockUser(username)
    fun `Uploading a file with user and project should work`() {
        val file = MockMultipartFile("liite", "text.txt", "text/plain", "ABC".toByteArray())
        val hankeTunnus = "HAI-123"
        val hankeId = 123
        val userId = currentUserId()
        val uuid = UUID.randomUUID()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
            .returns(true)
        every { attachmentService.add(hankeTunnus, file) }
            .returns(
                HankeAttachment(
                    id = uuid,
                    name = "text.txt",
                    user = username,
                    created = DateFactory.getEndDatetime().toLocalDateTime(),
                    AttachmentTila.PENDING,
                    hankeId
                )
            )

        send(hankeTunnus, file).andExpect(status().`is`(200))

        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.EDIT) }
        verify { attachmentService.add(hankeTunnus, file) }
    }

    @Test
    @WithMockUser(username)
    fun `List of attachments can be loaded`() {
        val hankeTunnus = "HAI-123"
        val hankeId = 123
        val userId = currentUserId()

        every { hankeService.getHankeId(hankeTunnus) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, PermissionCode.VIEW) }
            .returns(true)
        every { attachmentService.getHankeAttachments(hankeTunnus) }
            .returns(
                listOf(
                    AttachmentFactory.create(hankeId, name = "file1.pdf"),
                    AttachmentFactory.create(hankeId, name = "file2.pdf"),
                    AttachmentFactory.create(hankeId, name = "file3.pdf"),
                )
            )

        get("/liitteet/hanke/${hankeTunnus}").andExpect(status().`is`(200))

        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(hankeId, userId, PermissionCode.VIEW) }
        verify { attachmentService.getHankeAttachments(hankeTunnus) }
    }
}
