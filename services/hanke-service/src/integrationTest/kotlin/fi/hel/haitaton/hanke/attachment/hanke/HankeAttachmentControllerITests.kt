package fi.hel.haitaton.hanke.attachment.hanke

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeError.HAI0001
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.HANKE_TUNNUS
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentUploadService
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.TestHankeIdentifier
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verifyOrder
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HankeAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ContextConfiguration
class HankeAttachmentControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hankeAttachmentService: HankeAttachmentService
    @Autowired private lateinit var attachmentUploadService: AttachmentUploadService
    @Autowired private lateinit var authorizer: HankeAttachmentAuthorizer

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hankeAttachmentService, authorizer)
    }

    @Test
    fun `getMetadataList when valid request should return metadata list`() {
        val data = (1..3).map { HankeAttachmentFactory.create(fileName = "${it}file.pdf") }
        every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true
        every { hankeAttachmentService.getMetadataList(HANKE_TUNNUS) } returns data

        getMetadataList().andExpect(status().isOk)

        verifyOrder {
            authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
            hankeAttachmentService.getMetadataList(HANKE_TUNNUS)
        }
    }

    @Test
    fun `getAttachmentContent when valid request should return attachment file`() {
        val liiteId = UUID.fromString("19d6a9f7-afb0-469f-b570-cba0d10b03fc")
        every { authorizer.authorizeAttachment(HANKE_TUNNUS, liiteId, VIEW.name) } returns true
        every { hankeAttachmentService.getContent(liiteId) } returns
            AttachmentContent(FILE_NAME_PDF, APPLICATION_PDF_VALUE, DUMMY_DATA)

        getAttachmentContent(attachmentId = liiteId)
            .andExpect(status().isOk)
            .andExpect(header().string(CONTENT_DISPOSITION, "attachment; filename=$FILE_NAME_PDF"))
            .andExpect(content().bytes(DUMMY_DATA))

        verifyOrder {
            authorizer.authorizeAttachment(HANKE_TUNNUS, liiteId, VIEW.name)
            hankeAttachmentService.getContent(liiteId)
        }
    }

    @Test
    fun `postAttachment when valid request should succeed`() {
        val file = testFile()
        val hanke = TestHankeIdentifier(1, HANKE_TUNNUS)
        every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, EDIT.name) } returns true
        every { attachmentUploadService.uploadHankeAttachment(hanke.hankeTunnus, file) } returns
            HankeAttachmentFactory.create()

        postAttachment(file = file).andExpect(status().isOk)

        verifyOrder {
            authorizer.authorizeHankeTunnus(HANKE_TUNNUS, EDIT.name)
            attachmentUploadService.uploadHankeAttachment(hanke.hankeTunnus, file)
        }
    }

    @Test
    fun `postAttachment when attachment invalid or amount exceeded return bad request`() {
        val file = testFile()
        val hanke = TestHankeIdentifier(1, HANKE_TUNNUS)
        every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, EDIT.name) } returns true
        every { attachmentUploadService.uploadHankeAttachment(hanke.hankeTunnus, file) } throws
            AttachmentInvalidException("Something went wrong")

        postAttachment(file = file).andExpect(status().isBadRequest)

        verifyOrder {
            authorizer.authorizeHankeTunnus(HANKE_TUNNUS, EDIT.name)
            attachmentUploadService.uploadHankeAttachment(hanke.hankeTunnus, file)
        }
    }

    @Test
    fun `postAttachment when no rights for hanke should fail`() {
        every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, EDIT.name) } throws
            HankeNotFoundException(HANKE_TUNNUS)

        postAttachment().andExpect(status().isNotFound)

        verifyOrder { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, EDIT.name) }
    }

    @Test
    fun `deleteAttachment when valid request should succeed`() {
        val liiteId = UUID.fromString("357bb2e4-9464-4aff-9e66-f4b2764ffbf4")
        every { authorizer.authorizeAttachment(HANKE_TUNNUS, liiteId, EDIT.name) } returns true
        justRun { hankeAttachmentService.deleteAttachment(liiteId) }

        deleteAttachment(attachmentId = liiteId).andExpect(status().isOk)

        verifyOrder {
            authorizer.authorizeAttachment(HANKE_TUNNUS, liiteId, EDIT.name)
            hankeAttachmentService.deleteAttachment(liiteId)
        }
    }

    @Test
    @WithAnonymousUser
    fun `unauthorized without authenticated user`() {
        getMetadataList().andExpectError(HAI0001)
        getAttachmentContent(resultType = APPLICATION_JSON).andExpectError(HAI0001)
        postAttachment().andExpectError(HAI0001)
        deleteAttachment().andExpectError(HAI0001)
    }

    private fun getMetadataList(hankeTunnus: String = HANKE_TUNNUS): ResultActions =
        get("/hankkeet/$hankeTunnus/liitteet")

    private fun getAttachmentContent(
        hankeTunnus: String = HANKE_TUNNUS,
        attachmentId: UUID = UUID.fromString("919a765f-3ef0-46f0-a52b-9b47024ac33c"),
        resultType: MediaType = APPLICATION_PDF,
    ): ResultActions =
        get("/hankkeet/$hankeTunnus/liitteet/$attachmentId/content", resultType = resultType)

    private fun postAttachment(
        hankeTunnus: String = HANKE_TUNNUS,
        file: MockMultipartFile = testFile()
    ): ResultActions {
        return mockMvc.perform(multipart("/hankkeet/$hankeTunnus/liitteet").file(file).with(csrf()))
    }

    private fun deleteAttachment(
        hankeTunnus: String = HANKE_TUNNUS,
        attachmentId: UUID = UUID.fromString("68cbd5fd-bf5f-4665-b157-d1eca6800f0c"),
    ): ResultActions = delete("/hankkeet/$hankeTunnus/liitteet/$attachmentId")
}

@WebMvcTest(
    HankeAttachmentController::class,
    properties = ["haitaton.features.hanke-editing=false"]
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeAttachmentControllerHankeEditingDisabledITests(
    @Autowired override val mockMvc: MockMvc
) : ControllerTest {

    @Test
    fun `post attachment when hanke editing is disabled should return 404`() {
        mockMvc
            .perform(multipart("/hankkeet/$HANKE_TUNNUS/liitteet").file(testFile()).with(csrf()))
            .andExpect(status().isNotFound)
            .andExpect(hankeError(HankeError.HAI0004))
    }

    @Test
    fun `delete attachment when hanke editing is disabled should return 404`() {
        delete("/hankkeet/$HANKE_TUNNUS/liitteet/4cf0c32d-710a-49a8-a496-6be50a8e0f40")
            .andExpect(status().isNotFound)
            .andExpect(hankeError(HankeError.HAI0004))
    }
}
