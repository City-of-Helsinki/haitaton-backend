package fi.hel.haitaton.hanke.attachment.application

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError.HAI0001
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.attachment.APPLICATION_ID
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.HANKE_ID
import fi.hel.haitaton.hanke.attachment.HANKE_TUNNUS
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentScanStatus
import fi.hel.haitaton.hanke.attachment.dummyData
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.createApplication
import fi.hel.haitaton.hanke.factory.AttachmentFactory.applicationAttachmentMetadata
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ApplicationAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME)
class ApplicationAttachmentControllerITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    @Autowired private lateinit var applicationAttachmentService: ApplicationAttachmentService
    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            applicationAttachmentService,
            applicationService,
            hankeService,
            permissionService
        )
    }

    @Test
    fun `getMetadataList when valid request should return metadata list`() {
        val data = (1..3).map { applicationAttachmentMetadata(fileName = "${it}file.pdf") }
        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, VIEW) } returns true
        every { applicationAttachmentService.getMetadataList(APPLICATION_ID) } returns data

        val result: List<ApplicationAttachmentMetadata> =
            getMetadataList().andExpect(status().isOk).andReturnBody()

        assertThat(result).each { d ->
            d.transform { it.id }.isNotNull()
            d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
            d.transform { it.createdByUserId }.isEqualTo(USERNAME)
            d.transform { it.createdAt }.isNotNull()
            d.transform { it.scanStatus }.isEqualTo(AttachmentScanStatus.OK)
            d.transform { it.applicationId }.isEqualTo(APPLICATION_ID)
            d.transform { it.attachmentType }.isEqualTo(MUU)
        }
        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, VIEW)
            applicationAttachmentService.getMetadataList(APPLICATION_ID)
        }
    }

    @Test
    fun `getAttachmentContent when valid request should return attachment file`() {
        val attachmentId = randomUUID()

        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns (HANKE_ID)
        every { permissionService.hasPermission(HANKE_ID, USERNAME, VIEW) } returns true
        every { applicationAttachmentService.getContent(APPLICATION_ID, attachmentId) } returns
            AttachmentContent(FILE_NAME_PDF, APPLICATION_PDF_VALUE, dummyData)

        getAttachmentContent(attachmentId = attachmentId)
            .andExpect(status().isOk)
            .andExpect(header().string(CONTENT_DISPOSITION, "attachment; filename=$FILE_NAME_PDF"))
            .andExpect(content().contentType(APPLICATION_PDF))
            .andExpect(content().bytes(dummyData))

        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, VIEW)
            applicationAttachmentService.getContent(APPLICATION_ID, attachmentId)
        }
    }

    @Test
    fun `postAttachment when valid request should succeed`() {
        val file = testFile()
        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns true
        every { applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file) } returns
            applicationAttachmentMetadata()

        val result: ApplicationAttachmentMetadata =
            postAttachment(file = file).andExpect(status().isOk).andReturnBody()

        with(result) {
            assertThat(id).isNotNull()
            assertThat(fileName).isEqualTo(FILE_NAME_PDF)
            assertThat(createdByUserId).isEqualTo(USERNAME)
            assertThat(createdAt).isNotNull()
            assertThat(scanStatus).isEqualTo(AttachmentScanStatus.OK)
            assertThat(applicationId).isEqualTo(APPLICATION_ID)
            assertThat(attachmentType).isEqualTo(MUU)
        }
        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
            applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file)
        }
    }

    @Test
    fun `postAttachment when application processing should return conflict`() {
        val file = testFile()
        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns true
        every { applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file) } throws
            ApplicationAlreadyProcessingException(APPLICATION_ID, 123)

        postAttachment(file = file).andExpect(status().isConflict)

        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
            applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file)
        }
    }

    @Test
    fun `postAttachment when unknown application should fail`() {
        every { applicationService.getApplicationById(APPLICATION_ID) } throws
            ApplicationNotFoundException(APPLICATION_ID)

        postAttachment().andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(APPLICATION_ID) }
    }

    @Test
    fun `postAttachment when no rights for hanke should fail`() {
        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns false

        postAttachment().andExpect(status().isNotFound)

        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
        }
    }

    @Test
    fun `deleteAttachment when valid request should succeed`() {
        val attachmentId = randomUUID()
        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns true
        justRun { applicationAttachmentService.deleteAttachment(APPLICATION_ID, attachmentId) }

        deleteAttachment(attachmentId = attachmentId).andExpect(status().isOk)

        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
            applicationAttachmentService.deleteAttachment(APPLICATION_ID, attachmentId)
        }
    }

    @Test
    fun `deleteAttachment when application processing should return conflict`() {
        val attachmentId = randomUUID()
        every { applicationService.getApplicationById(APPLICATION_ID) } returns createApplication()
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns HANKE_ID
        every { permissionService.hasPermission(HANKE_ID, USERNAME, EDIT) } returns true
        every { applicationAttachmentService.deleteAttachment(APPLICATION_ID, attachmentId) } throws
            ApplicationAlreadyProcessingException(APPLICATION_ID, 123)

        deleteAttachment(attachmentId = attachmentId).andExpect(status().isConflict)

        verifyOrder {
            applicationService.getApplicationById(APPLICATION_ID)
            hankeService.getHankeId(HANKE_TUNNUS)
            permissionService.hasPermission(HANKE_ID, USERNAME, EDIT)
            applicationAttachmentService.deleteAttachment(APPLICATION_ID, attachmentId)
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

    private fun getMetadataList(applicationId: Long = APPLICATION_ID): ResultActions =
        get("/hakemukset/$applicationId/liitteet")

    private fun getAttachmentContent(
        applicationId: Long = APPLICATION_ID,
        attachmentId: UUID = randomUUID(),
    ): ResultActions = get("/hakemukset/$applicationId/liitteet/$attachmentId/content")

    private fun postAttachment(
        applicationId: Long = APPLICATION_ID,
        attachmentType: ApplicationAttachmentType = MUU,
        file: MockMultipartFile = testFile()
    ): ResultActions {
        return mockMvc.perform(
            multipart("/hakemukset/$applicationId/liitteet")
                .file(file)
                .param("tyyppi", attachmentType.toString())
                .with(csrf())
        )
    }

    private fun deleteAttachment(
        applicationId: Long = APPLICATION_ID,
        attachmentId: UUID = randomUUID()
    ): ResultActions = delete("/hakemukset/$applicationId/liitteet/$attachmentId")
}
