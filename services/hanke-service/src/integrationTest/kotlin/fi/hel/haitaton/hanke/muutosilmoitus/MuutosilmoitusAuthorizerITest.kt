package fi.hel.haitaton.hanke.muutosilmoitus

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
import fi.hel.haitaton.hanke.factory.MuutosilmoitusAttachmentFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
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

class MuutosilmoitusAuthorizerITest(
    @Autowired private val authorizer: MuutosilmoitusAuthorizer,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val attachmentFactory: MuutosilmoitusAttachmentFactory,
) : IntegrationTest() {
    @Nested
    inner class Authorize {

        @Test
        fun `throws exception when muutosilmoitus is not found`() {
            val muutosilmoitusId = UUID.fromString("8a6248bc-d562-4074-b016-e8074e4cea43")

            val failure = assertFailure {
                authorizer.authorize(muutosilmoitusId, PermissionCode.VIEW.name)
            }

            failure.all {
                hasClass(MuutosilmoitusNotFoundException::class.java)
                messageContains(muutosilmoitusId.toString())
                messageContains("Muutosilmoitus not found")
            }
        }

        @Test
        fun `throws exception when user doesn't have the required permission for the muutosilmoitus`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).saveEntity()

            val failure = assertFailure {
                authorizer.authorize(muutosilmoitus.id, PermissionCode.EDIT_APPLICATIONS.name)
            }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains(hakemus.id.toString())
            }
        }

        @Test
        fun `returns false when user has the a permission to a different hanke`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)
            val otherHanke = hankeFactory.saveMinimal()
            val hakemus = hakemusFactory.builder(otherHanke).saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).saveEntity()

            val failure = assertFailure {
                authorizer.authorize(muutosilmoitus.id, PermissionCode.EDIT_APPLICATIONS.name)
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
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).saveEntity()

            val result =
                authorizer.authorize(muutosilmoitus.id, PermissionCode.EDIT_APPLICATIONS.name)

            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class AuthorizeAttachment {
        private val muutosilmoitusId: UUID = UUID.fromString("1f163338-93ed-409c-8230-ab7041565c70")
        private val attachmentId = UUID.fromString("2f163338-93ed-409c-8230-ab7041565c70")

        @Test
        fun `throws exception if muutosilmoitus is not found`() {
            assertFailure {
                    authorizer.authorizeAttachment(muutosilmoitusId, attachmentId, VIEW.name)
                }
                .all {
                    hasClass(MuutosilmoitusNotFoundException::class)
                    messageContains(muutosilmoitusId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal()
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)
            val hakemus = hakemusFactory.builder(hanke).saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).saveEntity()

            assertFailure {
                    authorizer.authorizeAttachment(muutosilmoitus.id, attachmentId, EDIT.name)
                }
                .all {
                    hasClass(HakemusNotFoundException::class)
                    messageContains(hakemus.id.toString())
                }
        }

        @Test
        fun `throws exception if the attachment is not found`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().saveEntity()

            assertFailure {
                    authorizer.authorizeAttachment(muutosilmoitus.id, attachmentId, VIEW.name)
                }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachmentId.toString())
                }
        }

        @Test
        fun `throws exception if the attachment belongs to another muutosilmoitus`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().saveEntity()
            val muutosilmoitus2 = muutosilmoitusFactory.builder(alluId = 2).saveEntity()
            val attachment = attachmentFactory.save(muutosilmoitus = muutosilmoitus2.toDomain())

            assertFailure {
                    authorizer.authorizeAttachment(muutosilmoitus.id, attachment.id!!, VIEW.name)
                }
                .all {
                    hasClass(AttachmentNotFoundException::class)
                    messageContains(attachment.id.toString())
                }
        }

        @Test
        fun `returns true if the attachment is found, it belongs to the muutosilmoitus and the user has the correct permission`() {
            val attachment = attachmentFactory.save()

            assertThat(
                    authorizer.authorizeAttachment(
                        attachment.muutosilmoitusId,
                        attachment.id!!,
                        VIEW.name,
                    )
                )
                .isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val muutosilmoitus = muutosilmoitusFactory.builder().saveEntity()
            assertFailure {
                    authorizer.authorizeAttachment(muutosilmoitus.id, attachmentId, "Not real")
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
