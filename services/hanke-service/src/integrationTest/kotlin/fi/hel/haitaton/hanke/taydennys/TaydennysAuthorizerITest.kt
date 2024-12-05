package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TaydennysAuthorizerITest(
    @Autowired private val authorizer: TaydennysAuthorizer,
    @Autowired private val taydennysFactory: TaydennysFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val attachmentFactory: TaydennysAttachmentFactory,
) : IntegrationTest() {

    @Nested
    inner class AuthorizeAttachment {
        private val taydennysId: UUID = UUID.fromString("1f163338-93ed-409c-8230-ab7041565c70")
        private val attachmentId = UUID.fromString("2f163338-93ed-409c-8230-ab7041565c70")

        @Test
        fun `throws exception if taydennys is not found`() {
            assertFailure { authorizer.authorizeAttachment(taydennysId, attachmentId, VIEW.name) }
                .all {
                    hasClass(TaydennysNotFoundException::class)
                    messageContains(taydennysId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus.id, hanke.id).saveEntity()

            assertFailure { authorizer.authorizeAttachment(taydennys.id, attachmentId, EDIT.name) }
                .all {
                    hasClass(HakemusNotFoundException::class)
                    messageContains(hakemus.id.toString())
                }
        }

        @Test
        fun `throws exception if the attachment is not found`() {
            val taydennys = taydennysFactory.builder().saveEntity()

            assertFailure { authorizer.authorizeAttachment(taydennys.id, attachmentId, VIEW.name) }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachmentId.toString())
                }
        }

        @Test
        fun `throws exception if the attachment belongs to another taydennys`() {
            val taydennys = taydennysFactory.builder().saveEntity()
            val taydennys2 = taydennysFactory.builder(id = taydennysId, alluId = 2).saveEntity()
            val attachment =
                attachmentFactory.save(taydennys = taydennys2.toDomain()).withContent().value

            assertFailure {
                    authorizer.authorizeAttachment(taydennys.id, attachment.id!!, VIEW.name)
                }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachment.id.toString())
                }
        }

        @Test
        fun `returns true if the attachment is found, it belongs to the taydennys and the user has the correct permission`() {
            val attachment = attachmentFactory.save().withContent().value

            assertThat(
                    authorizer.authorizeAttachment(
                        attachment.taydennysId,
                        attachment.id!!,
                        VIEW.name,
                    )
                )
                .isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val taydennys = taydennysFactory.builder().saveEntity()
            assertFailure { authorizer.authorizeAttachment(taydennys.id, attachmentId, "Not real") }
                .all {
                    hasClass(IllegalArgumentException::class)
                    messageContains(
                        "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                    )
                }
        }
    }
}
