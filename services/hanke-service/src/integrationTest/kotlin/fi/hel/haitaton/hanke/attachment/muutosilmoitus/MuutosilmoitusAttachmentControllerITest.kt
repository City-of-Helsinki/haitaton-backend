package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.attachment.APPLICATION_ID
import fi.hel.haitaton.hanke.attachment.DUMMY_DATA
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.attachment.common.AttachmentContent
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
import fi.hel.haitaton.hanke.attachment.testFile
import fi.hel.haitaton.hanke.factory.MuutosilmoitusAttachmentFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory.Companion.DEFAULT_ID
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusAlreadySentException
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusAuthorizer
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusNotFoundException
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
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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

@WebMvcTest(MuutosilmoitusAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class MuutosilmoitusAttachmentControllerITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    @Autowired private lateinit var attachmentService: MuutosilmoitusAttachmentService
    @Autowired private lateinit var authorizer: MuutosilmoitusAuthorizer

    private val attachmentId: UUID = UUID.fromString("2d3a1fac-53dc-4471-a7b8-ab527690c264")

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

        private val url = "/muutosilmoitukset/$DEFAULT_ID/liitteet/$attachmentId/content"

        @Test
        fun `returns 200 and attachment file when request is valid`() {
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } returns
                true
            every { attachmentService.getContent(attachmentId) } returns
                AttachmentContent(FILE_NAME_PDF, APPLICATION_PDF_VALUE, DUMMY_DATA)

            get(url, APPLICATION_PDF)
                .andExpect(status().isOk)
                .andExpect(header().string(CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(
                    header()
                        .string(
                            CONTENT_DISPOSITION,
                            containsString("filename*=UTF-8''$FILE_NAME_PDF"),
                        )
                )
                .andExpect(content().bytes(DUMMY_DATA))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name)
                attachmentService.getContent(attachmentId)
            }
        }

        @Test
        @WithAnonymousUser
        fun `returns 401 and error when user is unauthorized`() {
            get(url).andExpectError(HankeError.HAI0001)
        }

        @Test
        fun `returns 404 and error when user has no rights for application`() {
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } throws
                HakemusNotFoundException(APPLICATION_ID)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verifySequence { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) }
        }

        @Test
        fun `returns 403 and error when asking for valtakirja`() {
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } returns
                true
            every { attachmentService.getContent(attachmentId) } throws
                ValtakirjaForbiddenException(attachmentId)

            get(url).andExpect(status().isForbidden).andExpect(hankeError(HankeError.HAI3004))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name)
                attachmentService.getContent(attachmentId)
            }
        }

        @Test
        fun `returns 404 and error when attachment not found`() {
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } throws
                AttachmentNotFoundException(attachmentId)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI3002))

            verifySequence { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) }
        }

        @Test
        fun `returns 404 and error when muutosilmoitus not found`() {
            every { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) } throws
                MuutosilmoitusNotFoundException(DEFAULT_ID)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI7001))

            verifySequence { authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, VIEW.name) }
        }
    }

    @Nested
    inner class PostAttachment {
        @Test
        fun `returns 200 and metadata when request is valid`() {
            val file = testFile()
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } returns true
            every {
                attachmentService.addAttachment(DEFAULT_ID, ApplicationAttachmentType.MUU, file)
            } returns MuutosilmoitusAttachmentFactory.create()

            val result: MuutosilmoitusAttachmentMetadataDto =
                postAttachment(file = file).andExpect(status().isOk).andReturnBody()

            with(result) {
                assertThat(id).isNotNull()
                assertThat(fileName).isEqualTo(FILE_NAME_PDF)
                assertThat(createdByUserId).isEqualTo(USERNAME)
                assertThat(createdAt).isNotNull()
                assertThat(muutosilmoitusId).isEqualTo(DEFAULT_ID)
                assertThat(attachmentType).isEqualTo(ApplicationAttachmentType.MUU)
            }
            verifyOrder {
                authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name)
                attachmentService.addAttachment(DEFAULT_ID, ApplicationAttachmentType.MUU, file)
            }
        }

        @Test
        fun `returns 409 when muutosilmoitus has been sent already`() {
            val file = testFile()
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } returns true
            every {
                attachmentService.addAttachment(DEFAULT_ID, ApplicationAttachmentType.MUU, file)
            } throws
                MuutosilmoitusAlreadySentException(MuutosilmoitusFactory.create(id = DEFAULT_ID))

            postAttachment(file = file)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI7002))

            verifyOrder {
                authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name)
                attachmentService.addAttachment(DEFAULT_ID, ApplicationAttachmentType.MUU, file)
            }
        }

        @Test
        fun `returns 404 and error when user has no rights for hanke`() {
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } throws
                HankeNotFoundException("HAI24-1")

            postAttachment()
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI1001))

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        fun `returns 404 and error when application is not found`() {
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } throws
                HakemusNotFoundException(APPLICATION_ID)

            postAttachment()
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI2001))

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        fun `returns 404 and error when muutosilmoitus is not found`() {
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } throws
                MuutosilmoitusNotFoundException(DEFAULT_ID)

            postAttachment()
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI7001))

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        @WithAnonymousUser
        fun `returns 401 and error when user is unauthorized`() {
            postAttachment().andExpectError(HankeError.HAI0001)
        }

        private fun postAttachment(
            muutosilmoitusId: UUID = DEFAULT_ID,
            attachmentType: ApplicationAttachmentType = ApplicationAttachmentType.MUU,
            file: MockMultipartFile = testFile(),
        ): ResultActions {
            return mockMvc.perform(
                multipart("/muutosilmoitukset/$muutosilmoitusId/liitteet")
                    .file(file)
                    .param("tyyppi", attachmentType.toString())
                    .with(csrf())
            )
        }
    }

    @Nested
    inner class DeleteAttachment {

        private val url = "/muutosilmoitukset/$DEFAULT_ID/liitteet/$attachmentId"

        @Test
        fun `returns 200 and empty body when deletion succeeds`() {
            every {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            } returns true
            justRun { attachmentService.deleteAttachment(attachmentId) }

            delete(url).andExpect(status().isOk).andExpect(content().string(""))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
                attachmentService.deleteAttachment(attachmentId)
            }
        }

        @Test
        fun `returns 409 when muutosilmoitus has been sent`() {
            every {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            } returns true
            every { attachmentService.deleteAttachment(attachmentId) } throws
                MuutosilmoitusAlreadySentException(MuutosilmoitusFactory.create(id = DEFAULT_ID))

            delete(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI7002))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
                attachmentService.deleteAttachment(attachmentId)
            }
        }

        @Test
        @WithAnonymousUser
        fun `returns 401 and error when user is unauthorized`() {
            delete(url).andExpectError(HankeError.HAI0001)
        }

        @Test
        fun `returns 404 and error when user has no rights for application`() {
            every {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(APPLICATION_ID)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 404 and error when attachment not found`() {
            every {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            } throws AttachmentNotFoundException(attachmentId)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI3002))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 404 and error when muutosilmoitus not found`() {
            every {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            } throws MuutosilmoitusNotFoundException(DEFAULT_ID)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI7001))

            verifySequence {
                authorizer.authorizeAttachment(DEFAULT_ID, attachmentId, EDIT_APPLICATIONS.name)
            }
        }
    }
}
