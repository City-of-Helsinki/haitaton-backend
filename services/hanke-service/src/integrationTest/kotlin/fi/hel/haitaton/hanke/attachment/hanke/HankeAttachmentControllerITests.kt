package fi.hel.haitaton.hanke.attachment.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeError.HAI0001
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.HANKE_ID
import fi.hel.haitaton.hanke.attachment.HANKE_TUNNUS
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.dummyData
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AttachmentFactory.hankeAttachmentMetadata
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.UUID
import java.util.UUID.randomUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HankeAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME)
class HankeAttachmentControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hankeAttachmentService: HankeAttachmentService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hankeAttachmentService, hankeService, permissionService)
    }

    @Test
    fun `getMetadataList when valid request should return metadata list`() {
        val data = (1..3).map { hankeAttachmentMetadata(fileName = "${it}file.pdf") }
        every { hankeService.getHankeId(HANKE_TUNNUS) }.returns(HANKE_ID)
        every { permissionService.hasPermission(HANKE_ID, USERNAME, VIEW) } returns true
        every { hankeAttachmentService.getMetadataList(HANKE_TUNNUS) } returns data

        getMetadataList().andExpect(status().isOk)

        verifyOrder {
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, VIEW)
            hankeAttachmentService.getMetadataList(HANKE_TUNNUS)
        }
    }

    @Test
    fun `getAttachmentContent when valid request should return attachment file`() {
        val liiteId = randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { hankeAttachmentService.getContent(HANKE_TUNNUS, liiteId) } returns
            AttachmentContent(FILE_NAME_PDF, APPLICATION_PDF_VALUE, dummyData)
        every { permissionService.hasPermission(HANKE_ID, USERNAME, VIEW) } returns true

        getAttachmentContent(attachmentId = liiteId)
            .andExpect(status().isOk)
            .andExpect(header().string(CONTENT_DISPOSITION, "attachment; filename=$FILE_NAME_PDF"))
            .andExpect(content().contentType(APPLICATION_PDF))
            .andExpect(content().bytes(dummyData))

        verifyOrder {
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, VIEW)
            hankeAttachmentService.getContent(HANKE_TUNNUS, liiteId)
        }
    }

    @Test
    fun `postAttachment when valid request should succeed`() {
        val file = testFile()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns true
        every { hankeAttachmentService.addAttachment(HANKE_TUNNUS, file) } returns
            hankeAttachmentMetadata(fileName = "text.txt")

        postAttachment(file = file).andExpect(status().isOk)

        verifyOrder {
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
            hankeAttachmentService.addAttachment(HANKE_TUNNUS, file)
        }
    }

    @Test
    fun `postAttachment when unknown hankeTunnus should fail`() {
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns null

        postAttachment().andExpect(status().isNotFound)

        verify { hankeService.getHankeId(HANKE_TUNNUS) }
    }

    @Test
    fun `postAttachment when no rights for hanke should fail`() {
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns false

        postAttachment().andExpect(status().isNotFound)

        verifyOrder {
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
        }
    }

    @Test
    fun `deleteAttachment when valid request should succeed`() {
        val liiteId = randomUUID()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns true
        justRun { hankeAttachmentService.deleteAttachment(HANKE_TUNNUS, liiteId) }

        deleteAttachment(attachmentId = liiteId).andExpect(status().isOk)

        verifyOrder {
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
            hankeAttachmentService.deleteAttachment(HANKE_TUNNUS, liiteId)
        }
    }

    @Test
    @WithAnonymousUser
    fun `unauthorized without authenticated user`() {
        getMetadataList().andExpectError(HAI0001)
        getAttachmentContent().andExpectError(HAI0001)
        postAttachment().andExpectError(HAI0001)
        deleteAttachment().andExpectError(HAI0001)
    }

    private fun getMetadataList(hankeTunnus: String = HANKE_TUNNUS): ResultActions =
        get("/hankkeet/$hankeTunnus/liitteet")

    private fun getAttachmentContent(
        hankeTunnus: String = HANKE_TUNNUS,
        attachmentId: UUID = randomUUID(),
    ): ResultActions = get("/hankkeet/$hankeTunnus/liitteet/$attachmentId/content")

    private fun postAttachment(
        hankeTunnus: String = HANKE_TUNNUS,
        file: MockMultipartFile = testFile()
    ): ResultActions {
        return mockMvc.perform(multipart("/hankkeet/$hankeTunnus/liitteet").file(file).with(csrf()))
    }

    private fun deleteAttachment(
        hankeTunnus: String = HANKE_TUNNUS,
        attachmentId: UUID = randomUUID(),
    ): ResultActions = delete("/hankkeet/$hankeTunnus/liitteet/$attachmentId")
}

@WebMvcTest(HankeAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME)
@TestPropertySource(locations = ["classpath:application-test.properties"])
class HankeAttachmentControllerEndpointDisabledITests(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {

    @Test
    fun `post attachment when endpoint is disabled should return 404`() {
        val response =
            mockMvc
                .perform(
                    multipart("/hankkeet/$HANKE_TUNNUS/liitteet").file(testFile()).with(csrf())
                )
                .andExpect(status().isNotFound)
                .andReturn()
                .response

        assertThat(response.contentAsString).isEqualTo(expectedResponse())
    }

    @Test
    fun `delete attachment when endpoint is disabled should return 404`() {
        val response =
            delete("/hankkeet/$HANKE_TUNNUS/liitteet/${randomUUID()}").andReturn().response

        assertThat(response.contentAsString).isEqualTo(expectedResponse())
    }

    private fun expectedResponse(): String =
        with(HankeError.HAI0004) {
            return """{"errorMessage":"$errorMessage","errorCode":"$errorCode"}"""
        }
}
