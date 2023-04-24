package fi.hel.haitaton.hanke.attachment

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.factory.AttachmentFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
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
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header

private const val USERNAME = "username"
private const val FILE_PARAM = "liite"
private const val FILE_PDF = "file.pdf"
private const val FILE_TXT = "text.txt"
private const val HANKE_TUNNUS = "HAI-123"

@WebMvcTest(AttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class AttachmentControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var attachmentService: AttachmentService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    private val DUMMY_DATA = "ABC".toByteArray()

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
        val hankeId = 123
        val liiteId = UUID.randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(HANKE_TUNNUS, liiteId))
        every { permissionService.hasPermission(hankeId, USERNAME, VIEW) }.returns(false)

        get("/liite/${liiteId}").andExpect(MockMvcResultMatchers.status().`is`(404))

        verify { attachmentService.get(liiteId) }
        verify { permissionService.hasPermission(hankeId, USERNAME, VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Get attachment metadata`() {
        val hankeId = 123
        val liiteId = UUID.randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(HANKE_TUNNUS, liiteId, FILE_PDF))
        every { permissionService.hasPermission(hankeId, USERNAME, VIEW) }.returns(true)

        get("/liite/${liiteId}").andExpect(MockMvcResultMatchers.status().`is`(200))

        verify { attachmentService.get(liiteId) }
        verify { permissionService.hasPermission(hankeId, USERNAME, VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Get attachment data`() {
        val hankeId = 123
        val liiteId = UUID.randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(HANKE_TUNNUS, liiteId, FILE_PDF))
        every { attachmentService.getContent(liiteId) }.returns(byteArrayOf(1, 2, 3, 4))
        every { permissionService.hasPermission(hankeId, USERNAME, VIEW) }.returns(true)

        get("/liite/${liiteId}/content")
            .andExpect(MockMvcResultMatchers.status().`is`(200))
            .andExpect(header().string(CONTENT_DISPOSITION, "attachment; filename=${FILE_PDF}"))
            .andExpect(content().contentType(APPLICATION_PDF))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3, 4)))

        verify { attachmentService.get(liiteId) }
        verify { attachmentService.getContent(liiteId) }
        verify { permissionService.hasPermission(hankeId, USERNAME, VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Attachment can be removed`() {
        val hankeId = 123
        val userId = currentUserId()
        val liiteId = UUID.randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { attachmentService.get(liiteId) }
            .returns(AttachmentFactory.create(HANKE_TUNNUS, liiteId))
        every { permissionService.hasPermission(hankeId, userId, EDIT) }.returns(true)
        every { attachmentService.removeAttachment(liiteId) } just runs

        delete("/liite/${liiteId}").andExpect(MockMvcResultMatchers.status().`is`(200))

        verify { attachmentService.get(liiteId) }
        verify { permissionService.hasPermission(hankeId, userId, EDIT) }
        verify { attachmentService.removeAttachment(liiteId) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `List of attachments can be loaded`() {
        val hankeId = 123
        val userId = currentUserId()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, VIEW) }.returns(true)
        every { attachmentService.getHankeAttachments(HANKE_TUNNUS) }
            .returns(
                listOf(
                    AttachmentFactory.create(HANKE_TUNNUS, name = "file1.pdf"),
                    AttachmentFactory.create(HANKE_TUNNUS, name = "file2.pdf"),
                    AttachmentFactory.create(HANKE_TUNNUS, name = "file3.pdf"),
                )
            )

        get("/hanke/${HANKE_TUNNUS}/liite").andExpect(MockMvcResultMatchers.status().`is`(200))

        verify { hankeService.getHankeId(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(hankeId, userId, VIEW) }
        verify { attachmentService.getHankeAttachments(HANKE_TUNNUS) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Uploading a file with user and project should work`() {
        val file = MockMultipartFile(FILE_PARAM, FILE_TXT, TEXT_PLAIN_VALUE, DUMMY_DATA)
        val hankeId = 123
        val userId = currentUserId()
        val uuid = UUID.randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, EDIT) }.returns(true)
        every { attachmentService.add(HANKE_TUNNUS, file) }
            .returns(
                AttachmentMetadata(
                    id = uuid,
                    fileName = "text.txt",
                    createdByUserId = USERNAME,
                    createdAt = DateFactory.getEndDatetime().toLocalDateTime(),
                    scanStatus = AttachmentScanStatus.PENDING,
                    hankeTunnus = HANKE_TUNNUS,
                )
            )

        sendAttachment(file).andExpect(MockMvcResultMatchers.status().`is`(200))

        verify { hankeService.getHankeId(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(hankeId, userId, EDIT) }
        verify { attachmentService.add(HANKE_TUNNUS, file) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Uploading with unknown hankeTunnus should fail`() {
        val file = MockMultipartFile(FILE_PARAM, FILE_TXT, TEXT_PLAIN_VALUE, DUMMY_DATA)
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(null)

        sendAttachment(file).andExpect(MockMvcResultMatchers.status().`is`(404))

        verify { hankeService.getHankeId(HANKE_TUNNUS) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Upload should fail when no rights for hanke`() {
        val file = MockMultipartFile(FILE_PARAM, FILE_TXT, TEXT_PLAIN_VALUE, DUMMY_DATA)
        val hankeId = 123
        val userId = currentUserId()
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(hankeId)
        every { permissionService.hasPermission(hankeId, userId, EDIT) }.returns(false)

        sendAttachment(file).andExpect(MockMvcResultMatchers.status().`is`(404))

        verify { hankeService.getHankeId(HANKE_TUNNUS) }
        verify { permissionService.hasPermission(hankeId, userId, EDIT) }
    }

    @Test
    @WithAnonymousUser
    fun `Uploading without a session should fail`() {
        val file = MockMultipartFile(FILE_PARAM, FILE_TXT, TEXT_PLAIN_VALUE, DUMMY_DATA)

        sendAttachment(file).andExpect(MockMvcResultMatchers.status().`is`(401))
    }

    private fun sendAttachment(file: MockMultipartFile): ResultActions {
        return mockMvc.perform(
            MockMvcRequestBuilders.multipart("/hanke/${HANKE_TUNNUS}/liite")
                .file(file)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
        )
    }
}
