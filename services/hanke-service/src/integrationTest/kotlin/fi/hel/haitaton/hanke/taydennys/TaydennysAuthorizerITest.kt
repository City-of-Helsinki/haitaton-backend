package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeAlreadyCompletedException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode
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
    inner class Authorize {

        @Test
        fun `throws exception when taydennys is not found`() {
            val taydennysId = UUID.fromString("8a6248bc-d562-4074-b016-e8074e4cea43")

            val failure = assertFailure { authorizer.authorize(taydennysId, VIEW.name) }

            failure.all {
                hasClass(TaydennysNotFoundException::class.java)
                messageContains(taydennysId.toString())
                messageContains("Täydennys not found")
            }
        }

        @Test
        fun `throws exception when user doesn't have the required permission for the taydennys`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).saveEntity()

            val failure = assertFailure {
                authorizer.authorize(taydennys.id, PermissionCode.EDIT_APPLICATIONS.name)
            }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains(hakemus.id.toString())
            }
        }

        @Test
        fun `throws exception when user has the a permission to a different hanke`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)
            val otherHanke = hankeFactory.saveMinimal()
            val hakemus = hakemusFactory.builder(otherHanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).saveEntity()

            val failure = assertFailure {
                authorizer.authorize(taydennys.id, PermissionCode.EDIT_APPLICATIONS.name)
            }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains(hakemus.id.toString())
            }
        }

        @Test
        fun `returns true when user has the correct permission`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).saveEntity()

            val result = authorizer.authorize(taydennys.id, PermissionCode.EDIT_APPLICATIONS.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws exception when editing a taydennys for a completed hanke`() {
            val hanke = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).saveEntity()

            val failure = assertFailure {
                authorizer.authorize(taydennys.id, PermissionCode.EDIT_APPLICATIONS.name)
            }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=${hanke.id}")
            }
        }

        @Test
        fun `returns true when viewing a taydennys for a completed hanke`() {
            val hanke = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus).saveEntity()

            val result = authorizer.authorize(taydennys.id, VIEW.name)

            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class AuthorizeAttachment {
        private val taydennysId: UUID = UUID.fromString("1f163338-93ed-409c-8230-ab7041565c70")
        private val attachmentId = UUID.fromString("2f163338-93ed-409c-8230-ab7041565c70")

        @Test
        fun `throws exception if taydennys is not found`() {
            val failure = assertFailure {
                authorizer.authorizeAttachment(taydennysId, attachmentId, VIEW.name)
            }

            failure.all {
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

            val failure = assertFailure {
                authorizer.authorizeAttachment(taydennys.id, attachmentId, EDIT.name)
            }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains(hakemus.id.toString())
            }
        }

        @Test
        fun `throws exception if the attachment is not found`() {
            val taydennys = taydennysFactory.builder().saveEntity()

            val failure = assertFailure {
                authorizer.authorizeAttachment(taydennys.id, attachmentId, VIEW.name)
            }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachmentId.toString())
            }
        }

        @Test
        fun `throws exception if the attachment belongs to another taydennys`() {
            val taydennys = taydennysFactory.builder().saveEntity()
            val taydennys2 = taydennysFactory.builder(alluId = 2).saveEntity()
            val attachment =
                attachmentFactory.save(taydennys = taydennys2.toDomain()).withContent().value

            val failure = assertFailure {
                authorizer.authorizeAttachment(taydennys.id, attachment.id!!, VIEW.name)
            }

            failure.all {
                hasClass(AttachmentNotFoundException::class)
                messageContains(attachment.id.toString())
            }
        }

        @Test
        fun `returns true if the attachment is found, it belongs to the taydennys and the user has the correct permission`() {
            val attachment = attachmentFactory.save().withContent().value

            val result =
                authorizer.authorizeAttachment(attachment.taydennysId, attachment.id!!, VIEW.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val taydennys = taydennysFactory.builder().saveEntity()

            val failure = assertFailure {
                authorizer.authorizeAttachment(taydennys.id, attachmentId, "Not real")
            }

            failure.all {
                hasClass(IllegalArgumentException::class)
                messageContains(
                    "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                )
            }
        }

        @Test
        fun `throws exception when editing a taydennys attachment from a completed hanke`() {
            val hanke = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus.id, hanke.id).save()
            val attachment = attachmentFactory.save(taydennys = taydennys).withContent().value

            val failure = assertFailure {
                authorizer.authorizeAttachment(taydennys.id, attachment.id!!, EDIT.name)
            }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=${hanke.id}")
            }
        }

        @Test
        fun `returns true when viewing a taydennys attachment from a completed hanke`() {
            val hanke = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val taydennys = taydennysFactory.builder(hakemus.id, hanke.id).save()
            val attachment = attachmentFactory.save(taydennys = taydennys).withContent().value

            val result = authorizer.authorizeAttachment(taydennys.id, attachment.id!!, VIEW.name)

            assertThat(result).isTrue()
        }
    }
}
