package fi.hel.haitaton.hanke.attachment.application

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError.HAI0001
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.application.ApplicationAuthorizer
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.attachment.APPLICATION_ID
import fi.hel.haitaton.hanke.attachment.DEFAULT_SIZE
import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadataDto
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType.MUU
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT_APPLICATIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
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

@WebMvcTest(ApplicationAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ApplicationAttachmentControllerITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    @Autowired private lateinit var applicationAttachmentService: ApplicationAttachmentService
    @Autowired private lateinit var authorizer: ApplicationAuthorizer

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(applicationAttachmentService, authorizer)
    }

    @Nested
    inner class GetAttachments {
        @Test
        fun `when application not found should return 404`() {
            every { authorizer.authorizeApplicationId(APPLICATION_ID, VIEW.name) } throws
                ApplicationNotFoundException(APPLICATION_ID)

            get("/hakemukset/$APPLICATION_ID/liitteet").andExpect(status().isNotFound)

            verifySequence { authorizer.authorizeApplicationId(APPLICATION_ID, VIEW.name) }
        }

        @Test
        fun `when valid request should return metadata list`() {
            val data =
                (1..3).map { ApplicationAttachmentFactory.create(fileName = "$it$FILE_NAME_PDF") }
            every { authorizer.authorizeApplicationId(APPLICATION_ID, VIEW.name) } returns true
            every { applicationAttachmentService.getMetadataList(APPLICATION_ID) } returns data
            val result: List<ApplicationAttachmentMetadataDto> =
                get("/hakemukset/$APPLICATION_ID/liitteet").andExpect(status().isOk).andReturnBody()

            assertThat(result).each { d ->
                d.transform { it.id }.isNotNull()
                d.transform { it.fileName }.endsWith(FILE_NAME_PDF)
                d.transform { it.createdByUserId }.isEqualTo(USERNAME)
                d.transform { it.createdAt }.isNotNull()
                d.transform { it.applicationId }.isEqualTo(APPLICATION_ID)
                d.transform { it.attachmentType }.isEqualTo(MUU)
                d.transform { it.contentType }.isEqualTo(APPLICATION_PDF_VALUE)
                d.transform { it.size }.isEqualTo(DEFAULT_SIZE)
            }
            verifySequence {
                authorizer.authorizeApplicationId(APPLICATION_ID, VIEW.name)
                applicationAttachmentService.getMetadataList(APPLICATION_ID)
            }
        }

        @Test
        @WithAnonymousUser
        fun `unauthorized should return error`() {
            get("/hakemukset/$APPLICATION_ID/liitteet").andExpectError(HAI0001)
            getAttachmentContent(resultType = APPLICATION_JSON).andExpectError(HAI0001)
            postAttachment().andExpectError(HAI0001)
            deleteAttachment().andExpectError(HAI0001)
        }
    }

    @Nested
    inner class GetAttachmentContent {
        @Test
        fun `when valid request should return attachment file`() {
            val attachmentId = UUID.fromString("afc778b1-eb7c-4bad-951c-de70e173a757")
            every {
                authorizer.authorizeAttachment(APPLICATION_ID, attachmentId, VIEW.name)
            } returns true
            every { applicationAttachmentService.getContent(attachmentId) } returns
                AttachmentContent(FILE_NAME_PDF, APPLICATION_PDF_VALUE, DUMMY_DATA)

            getAttachmentContent(attachmentId = attachmentId)
                .andExpect(status().isOk)
                .andExpect(
                    header().string(CONTENT_DISPOSITION, "attachment; filename=$FILE_NAME_PDF")
                )
                .andExpect(content().bytes(DUMMY_DATA))

            verifySequence {
                authorizer.authorizeAttachment(APPLICATION_ID, attachmentId, VIEW.name)
                applicationAttachmentService.getContent(attachmentId)
            }
        }

        @Test
        @WithAnonymousUser
        fun `unauthorized should return error`() {
            getAttachmentContent(resultType = APPLICATION_JSON).andExpectError(HAI0001)
        }
    }

    @Nested
    inner class PostAttachment {
        @Test
        fun `when valid request should succeed`() {
            val file = testFile()
            every {
                authorizer.authorizeApplicationId(APPLICATION_ID, EDIT_APPLICATIONS.name)
            } returns true
            every { applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file) } returns
                ApplicationAttachmentFactory.createDto()

            val result: ApplicationAttachmentMetadataDto =
                postAttachment(file = file).andExpect(status().isOk).andReturnBody()

            with(result) {
                assertThat(id).isNotNull()
                assertThat(fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(createdByUserId).isEqualTo(USERNAME)
                assertThat(createdAt).isNotNull()
                assertThat(applicationId).isEqualTo(APPLICATION_ID)
                assertThat(attachmentType).isEqualTo(MUU)
            }
            verifyOrder {
                authorizer.authorizeApplicationId(APPLICATION_ID, EDIT_APPLICATIONS.name)
                applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file)
            }
        }

        @Test
        fun `when application is sent to Allu should return conflict`() {
            val file = testFile()
            every {
                authorizer.authorizeApplicationId(APPLICATION_ID, EDIT_APPLICATIONS.name)
            } returns true
            every { applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file) } throws
                ApplicationInAlluException(APPLICATION_ID, 123)

            postAttachment(file = file).andExpect(status().isConflict)

            verifyOrder {
                authorizer.authorizeApplicationId(APPLICATION_ID, EDIT_APPLICATIONS.name)
                applicationAttachmentService.addAttachment(APPLICATION_ID, MUU, file)
            }
        }

        @Test
        fun `when no rights for hanke should fail`() {
            every {
                authorizer.authorizeApplicationId(APPLICATION_ID, EDIT_APPLICATIONS.name)
            } throws ApplicationNotFoundException(APPLICATION_ID)

            postAttachment().andExpect(status().isNotFound)

            verifyOrder {
                authorizer.authorizeApplicationId(APPLICATION_ID, EDIT_APPLICATIONS.name)
            }
        }

        @Test
        @WithAnonymousUser
        fun `unauthorized should return error`() {
            postAttachment().andExpectError(HAI0001)
        }
    }

    @Nested
    inner class DeleteAttachment {
        @Test
        fun `when valid request should succeed`() {
            val attachmentId = UUID.fromString("5c97dcf2-686f-4cd6-9b9d-4124aff23a07")
            every {
                authorizer.authorizeAttachment(APPLICATION_ID, attachmentId, EDIT_APPLICATIONS.name)
            } returns true
            justRun { applicationAttachmentService.deleteAttachment(attachmentId) }

            deleteAttachment(attachmentId = attachmentId).andExpect(status().isOk)

            verifySequence {
                authorizer.authorizeAttachment(APPLICATION_ID, attachmentId, EDIT_APPLICATIONS.name)
                applicationAttachmentService.deleteAttachment(attachmentId)
            }
        }

        @Test
        fun `when application is sent to Allu should return conflict`() {
            val attachmentId = UUID.fromString("48de0b68-1070-47c1-b760-195bac6261f4")
            val alluId = 123
            every {
                authorizer.authorizeAttachment(APPLICATION_ID, attachmentId, EDIT_APPLICATIONS.name)
            } returns true
            every { applicationAttachmentService.deleteAttachment(attachmentId) } throws
                ApplicationInAlluException(APPLICATION_ID, alluId)

            deleteAttachment(attachmentId = attachmentId).andExpect(status().isConflict)

            verifySequence {
                authorizer.authorizeAttachment(APPLICATION_ID, attachmentId, EDIT_APPLICATIONS.name)
                applicationAttachmentService.deleteAttachment(attachmentId)
            }
        }

        @Test
        @WithAnonymousUser
        fun `unauthorized should return error`() {
            deleteAttachment().andExpectError(HAI0001)
        }
    }

    private fun getAttachmentContent(
        applicationId: Long = APPLICATION_ID,
        attachmentId: UUID = UUID.fromString("df37fe12-fb36-4f61-8b07-2fb4ae8233f8"),
        resultType: MediaType = APPLICATION_PDF,
    ): ResultActions = get("/hakemukset/$applicationId/liitteet/$attachmentId/content", resultType)

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
        attachmentId: UUID = UUID.fromString("5f79cc05-6a5e-4bb9-b457-f7df0e5e5471"),
    ): ResultActions = delete("/hakemukset/$applicationId/liitteet/$attachmentId")
}
