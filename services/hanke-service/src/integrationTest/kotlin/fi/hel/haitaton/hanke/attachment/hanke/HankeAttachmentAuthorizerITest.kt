package fi.hel.haitaton.hanke.attachment.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeAlreadyCompletedException
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HankeAttachmentAuthorizerITest(
    @Autowired private val authorizer: HankeAttachmentAuthorizer,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
) : IntegrationTest() {
    private val hankeTunnus = "HAI24-14"
    private val attachmentId = UUID.fromString("3b0e3149-37a2-4393-af03-6a34b946fef1")

    @Nested
    inner class AuthorizeAttachment {
        @Test
        fun `throws exception if hankeTunnus is not found`() {
            val failure = assertFailure {
                authorizer.authorizeAttachment(hankeTunnus, attachmentId, VIEW.name)
            }

            failure.all {
                hasClass(HankeNotFoundException::class)
                messageContains(hankeTunnus)
            }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)

            val failure = assertFailure {
                authorizer.authorizeAttachment(hankeTunnus, attachmentId, VIEW.name)
            }

            failure.all {
                hasClass(HankeNotFoundException::class)
                messageContains(hankeTunnus)
            }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            val failure = assertFailure {
                authorizer.authorizeAttachment(hankeTunnus, attachmentId, EDIT.name)
            }

            failure.all {
                hasClass(HankeNotFoundException::class)
                messageContains(hankeTunnus)
            }
        }

        @Test
        fun `throws exception if the attachment is not found`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            val failure = assertFailure {
                authorizer.authorizeAttachment(hankeTunnus, attachmentId, VIEW.name)
            }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Test
        fun `throws exception if the attachment belongs to another hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val attachment = hankeAttachmentFactory.save().value
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            val failure = assertFailure {
                authorizer.authorizeAttachment(hankeTunnus, attachment.id!!, VIEW.name)
            }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachment.id.toString())
            }
        }

        @Test
        fun `returns true if the attachment is found, it belongs to the hanke and the user has the correct permission`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val attachment = hankeAttachmentFactory.save(hanke = hanke).value
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            val result = authorizer.authorizeAttachment(hankeTunnus, attachment.id!!, VIEW.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws an exception when modifying an attachment from a completed hanke`() {
            val hanke = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED)
            val attachment = hankeAttachmentFactory.save(hanke = hanke).value
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val failure = assertFailure {
                authorizer.authorizeAttachment(hanke.hankeTunnus, attachment.id!!, EDIT.name)
            }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=${hanke.id}")
            }
        }

        @Test
        fun `returns true when viewing an attachment from a completed hanke`() {
            val hanke = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED)
            val attachment = hankeAttachmentFactory.save(hanke = hanke).value
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val result =
                authorizer.authorizeAttachment(hanke.hankeTunnus, attachment.id!!, VIEW.name)

            assertThat(result).isTrue()
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
