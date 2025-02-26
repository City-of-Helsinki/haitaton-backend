package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MuutosilmoitusAuthorizerITest(
    @Autowired private val authorizer: MuutosilmoitusAuthorizer,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val permissionService: PermissionService,
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
}
