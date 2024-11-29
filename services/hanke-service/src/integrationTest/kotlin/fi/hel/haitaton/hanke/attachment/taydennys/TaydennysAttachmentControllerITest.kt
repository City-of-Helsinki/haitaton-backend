package fi.hel.haitaton.hanke.attachment.taydennys

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeError.HAI0001
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.attachment.APPLICATION_ID
import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.TaydennysAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory.Companion.DEFAULT_ID
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT_APPLICATIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.taydennys.TaydennysAuthorizer
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifyOrder
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(TaydennysAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TaydennysAttachmentControllerITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    @Autowired private lateinit var attachmentService: TaydennysAttachmentService
    @Autowired private lateinit var authorizer: TaydennysAuthorizer

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(attachmentService, authorizer)
    }

    @Nested
    inner class GetAttachmentContent {
        @Test
        fun `when valid request should return attachment file`() {
            val attachmentId = UUID.fromString("afc778b1-eb7c-4bad-951c-de70e173a757")
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } returns
                true
            every { attachmentService.getContent(attachmentId) } returns
                AttachmentContent(FILE_NAME_PDF, APPLICATION_PDF_VALUE, DUMMY_DATA)

            getAttachmentContent(attachmentId = attachmentId)
                .andExpect(status().isOk)
                .andExpect(
                    header().string(CONTENT_DISPOSITION, "attachment; filename=$FILE_NAME_PDF")
                )
                .andExpect(content().bytes(DUMMY_DATA))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name)
                attachmentService.getContent(attachmentId)
            }
        }

        @Test
        @WithAnonymousUser
        fun `unauthorized should return error`() {
            getAttachmentContent(resultType = APPLICATION_JSON).andExpectError(HAI0001)
        }

        @Test
        fun `returns 403 when asking for valtakirja`() {
            val attachmentId = UUID.fromString("afc778b1-eb7c-4bad-951c-de70e173a757")
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } returns
                true
            every { attachmentService.getContent(attachmentId) } throws
                ValtakirjaForbiddenException(attachmentId)

            get("/taydennykset/$DEFAULT_ID/liitteet/$attachmentId/content")
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI3004))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name)
                attachmentService.getContent(attachmentId)
            }
        }

        private fun getAttachmentContent(
            taydennysId: UUID = DEFAULT_ID,
            attachmentId: UUID = UUID.fromString("df37fe12-fb36-4f61-8b07-2fb4ae8233f8"),
            resultType: MediaType = APPLICATION_PDF,
        ): ResultActions =
            get("/taydennykset/$taydennysId/liitteet/$attachmentId/content", resultType)
    }

    @Nested
    inner class PostAttachment {
        @Test
        fun `when valid request should succeed`() {
            val file = testFile()
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } returns true
            every { attachmentService.addAttachment(DEFAULT_ID, MUU, file) } returns
                TaydennysAttachmentFactory.createDto()

            val result: TaydennysAttachmentMetadataDto =
                postAttachment(file = file).andExpect(status().isOk).andReturnBody()

            with(result) {
                assertThat(id).isNotNull()
                assertThat(fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(createdByUserId).isEqualTo(USERNAME)
                assertThat(createdAt).isNotNull()
                assertThat(taydennysId).isEqualTo(DEFAULT_ID)
                assertThat(attachmentType).isEqualTo(MUU)
            }
            verifyOrder {
                authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name)
                attachmentService.addAttachment(DEFAULT_ID, MUU, file)
            }
        }

        @Test
        fun `when no rights for hanke should fail`() {
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } throws
                HakemusNotFoundException(APPLICATION_ID)

            postAttachment().andExpect(status().isNotFound)

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        @WithAnonymousUser
        fun `unauthorized should return error`() {
            postAttachment().andExpectError(HAI0001)
        }
    }

    private fun postAttachment(
        taydennysId: UUID = DEFAULT_ID,
        attachmentType: ApplicationAttachmentType = MUU,
        file: MockMultipartFile = testFile(),
    ): ResultActions {
        return mockMvc.perform(
            multipart("/taydennykset/$taydennysId/liitteet")
                .file(file)
                .param("tyyppi", attachmentType.toString())
                .with(csrf())
        )
    }
}
