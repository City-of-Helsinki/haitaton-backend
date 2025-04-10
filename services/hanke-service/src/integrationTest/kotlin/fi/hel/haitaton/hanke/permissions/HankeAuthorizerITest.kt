package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeAlreadyCompletedException
import fi.hel.haitaton.hanke.HankeAuthorizer
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HankeAuthorizerITest(
    @Autowired private val authorizer: HankeAuthorizer,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val permissionService: PermissionService,
) : IntegrationTest() {

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
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure { authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.EDIT.name) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `return true if user has the required permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertThat(authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.VIEW.name))
                .isTrue()
        }

        @Test
        fun `throws exception when editing a completed hanke`() {
            val hanke =
                hankeFactory.saveMinimal(hankeTunnus = hankeTunnus, status = HankeStatus.COMPLETED)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val failure = assertFailure {
                authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.EDIT.name)
            }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=${hanke.id}")
            }
        }

        @Test
        fun `return true when viewing a completed hanke`() {
            val hanke =
                hankeFactory.saveMinimal(hankeTunnus = hankeTunnus, status = HankeStatus.COMPLETED)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val result = authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.VIEW.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val failure = assertFailure { authorizer.authorizeHankeTunnus(hankeTunnus, "Not real") }

            failure.all {
                hasClass(IllegalArgumentException::class)
                messageContains(
                    "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                )
            }
        }
    }
}
