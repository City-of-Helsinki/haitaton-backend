package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.factory.HankeFactory
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

private const val USERNAME = "test7358"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeAuthorizerITest(
    @Autowired private val authorizer: HankeAuthorizer,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val permissionService: PermissionService,
) : DatabaseTest() {

    val hankeTunnus = "HAI24-14"

    @Nested
    inner class AuthorizeHankeTunnus {
        @Test
        fun `throws exception if hankeTunnus is not found`() {
            assertFailure { authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.VIEW.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)

            assertFailure { authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.VIEW.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id!!, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure { authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.EDIT.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `return true if user has the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id!!, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertThat(authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.VIEW.name))
                .isTrue()
        }
    }
}
