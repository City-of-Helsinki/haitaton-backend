package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.attachment.USERNAME
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.FileClient
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentContentRepository
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeAttachmentAuthorizerITest(
    @Autowired private val authorizer: HankeAttachmentAuthorizer,
    @Autowired private val permissionService: PermissionService,
    @Autowired override val hankeFactory: HankeFactory,
    @Autowired override val hankeAttachmentRepository: HankeAttachmentRepository,
    @Autowired override val hankeAttachmentContentRepository: HankeAttachmentContentRepository,
    @Autowired override val fileClient: FileClient,
) : DatabaseTest(), HankeAttachmentFactory {
    private val hankeTunnus = "HAI24-14"
    private val attachmentId = UUID.fromString("3b0e3149-37a2-4393-af03-6a34b946fef1")

    @Nested
    inner class AuthorizeAttachment {
        @Test
        fun `throws exception if hankeTunnus is not found`() {
            assertFailure { authorizer.authorizeAttachment(hankeTunnus, attachmentId, VIEW.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)

            assertFailure { authorizer.authorizeAttachment(hankeTunnus, attachmentId, VIEW.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure { authorizer.authorizeAttachment(hankeTunnus, attachmentId, EDIT.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `throws exception if the attachment is not found`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure { authorizer.authorizeAttachment(hankeTunnus, attachmentId, VIEW.name) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachmentId.toString())
                }
        }

        @Test
        fun `throws exception if the attachment belongs to another hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val attachment = saveAttachment()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure {
                    authorizer.authorizeAttachment(hankeTunnus, attachment.id!!, VIEW.name)
                }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachment.id.toString())
                }
        }

        @Test
        fun `returns true if the attachment is found, it belongs to the hanke and the user has the correct permission`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val attachment = saveAttachment(hanke = hanke)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertThat(authorizer.authorizeAttachment(hankeTunnus, attachment.id!!, VIEW.name))
                .isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            assertFailure { authorizer.authorizeAttachment(hankeTunnus, attachmentId, "Not real") }
                .all {
                    hasClass(IllegalArgumentException::class)
                    messageContains(
                        "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                    )
                }
        }
    }
}
