package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
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

class HakemusAuthorizerITest(
    @Autowired private val authorizer: HakemusAuthorizer,
    @Autowired private val applicationFactory: ApplicationFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val applicationAttachmentFactory: ApplicationAttachmentFactory,
) : IntegrationTest() {

    val applicationId = 987654321L

    @Nested
    inner class AuthorizeHakemusId {
        @Test
        fun `throws exception if application doesn't exist`() {
            assertFailure { authorizer.authorizeHakemusId(applicationId, VIEW.name) }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(applicationId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have a permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal()
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)

            assertFailure { authorizer.authorizeHakemusId(application.id, VIEW.name) }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission`() {
            val hanke = hankeFactory.saveMinimal()
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    authorizer.authorizeHakemusId(application.id, PermissionCode.DELETE.name)
                }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `returns true if user has the required permission`() {
            val hanke = hankeFactory.saveMinimal()
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertThat(authorizer.authorizeHakemusId(application.id, VIEW.name)).isTrue()
        }
    }

    @Nested
    inner class AuthorizeCreate {
        private val hankeTunnus = "HAI23-1414"

        @Test
        fun `throws exception if hanketunnus not found`() {
            val request =
                CreateHakemusRequestFactory.johtoselvitysRequest(hankeTunnus = hankeTunnus)

            assertFailure { authorizer.authorizeCreate(request) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains("hankeTunnus $hankeTunnus")
                }
        }

        @Test
        fun `throws exception if user doesn't have EDIT_APPLICATIONS permission`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val request =
                CreateHakemusRequestFactory.johtoselvitysRequest(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure { authorizer.authorizeCreate(request) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains("hankeTunnus $hankeTunnus")
                }
        }

        @Test
        fun `returns true if user has EDIT_APPLICATIONS permission`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val request =
                CreateHakemusRequestFactory.johtoselvitysRequest(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)

            assertThat(authorizer.authorizeCreate(request)).isTrue()
        }
    }

    @Nested
    inner class AuthorizeAttachment {
        private val attachmentId = UUID.fromString("1f163338-93ed-409c-8230-ab7041565c70")

        @Test
        fun `throws exception if applicationId is not found`() {
            assertFailure { authorizer.authorizeAttachment(applicationId, attachmentId, VIEW.name) }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(applicationId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal()
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)

            assertFailure {
                    authorizer.authorizeAttachment(application.id, attachmentId, VIEW.name)
                }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)

            assertFailure {
                    authorizer.authorizeAttachment(application.id, attachmentId, EDIT.name)
                }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `throws exception if the attachment is not found`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)

            assertFailure {
                    authorizer.authorizeAttachment(application.id, attachmentId, VIEW.name)
                }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachmentId.toString())
                }
        }

        @Test
        fun `throws exception if the attachment belongs to another application`() {
            val hanke = hankeFactory.saveMinimal()
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)
            val hanke2 = hankeFactory.saveMinimal()
            val application2 = applicationFactory.saveApplicationEntity(USERNAME, hanke2)
            val attachment =
                applicationAttachmentFactory.save(application = application2).withContent().value
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure {
                    authorizer.authorizeAttachment(application.id, attachment.id!!, VIEW.name)
                }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachment.id.toString())
                }
        }

        @Test
        fun `returns true if the attachment is found, it belongs to the application and the user has the correct permission`() {
            val hanke = hankeFactory.saveMinimal()
            val application = applicationFactory.saveApplicationEntity(USERNAME, hanke)
            val attachment =
                applicationAttachmentFactory.save(application = application).withContent().value
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertThat(authorizer.authorizeAttachment(application.id, attachment.id!!, VIEW.name))
                .isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            assertFailure {
                    authorizer.authorizeAttachment(applicationId, attachmentId, "Not real")
                }
                .all {
                    hasClass(IllegalArgumentException::class)
                    messageContains(
                        "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                    )
                }
        }
    }
}
