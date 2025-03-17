package fi.hel.haitaton.hanke.attachment.muutosilmoitus

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeError.HAI0001
import fi.hel.haitaton.hanke.HankeError.HAI1001
import fi.hel.haitaton.hanke.HankeError.HAI2001
import fi.hel.haitaton.hanke.HankeError.HAI7001
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.attachment.APPLICATION_ID
import fi.hel.haitaton.hanke.attachment.FILE_NAME_PDF
import fi.hel.haitaton.hanke.attachment.andExpectError
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
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
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifyOrder
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(MuutosilmoitusAttachmentController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class MuutosilmoitusAttachmentControllerITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    @Autowired private lateinit var attachmentService: MuutosilmoitusAttachmentService
    @Autowired private lateinit var authorizer: MuutosilmoitusAuthorizer

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

            postAttachment().andExpect(status().isNotFound).andExpect(hankeError(HAI1001))

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        fun `returns 404 and error when application is not found`() {
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } throws
                HakemusNotFoundException(APPLICATION_ID)

            postAttachment().andExpect(status().isNotFound).andExpect(hankeError(HAI2001))

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        fun `returns 404 and error when muutosilmoitus is not found`() {
            every { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) } throws
                MuutosilmoitusNotFoundException(DEFAULT_ID)

            postAttachment().andExpect(status().isNotFound).andExpect(hankeError(HAI7001))

            verifyOrder { authorizer.authorize(DEFAULT_ID, EDIT_APPLICATIONS.name) }
        }

        @Test
        @WithAnonymousUser
        fun `returns 401 and error when user is unauthorized`() {
            postAttachment().andExpectError(HAI0001)
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
}
