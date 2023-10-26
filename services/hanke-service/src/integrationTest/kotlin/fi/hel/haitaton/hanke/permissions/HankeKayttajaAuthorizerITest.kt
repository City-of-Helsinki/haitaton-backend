package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import java.util.UUID
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
class HankeKayttajaAuthorizerITest(
    @Autowired private val authorizer: HankeKayttajaAuthorizer,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val permissionService: PermissionService,
) : DatabaseTest() {

    @Nested
    inner class AuthorizeKayttajaId {
        @Test
        fun `throws exception if kayttajaId is not found`() {
            val kayttajaId = UUID.fromString("bb201bf3-58b7-48d5-a7e4-e358370f74e1")

            assertFailure { authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.VIEW.name) }
                .all {
                    hasClass(HankeKayttajaNotFoundException::class)
                    messageContains(kayttajaId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveUser(hankeId).id

            assertFailure { authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.VIEW.name) }
                .all {
                    hasClass(HankeKayttajaNotFoundException::class)
                    messageContains(kayttajaId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveUserAndPermission(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            assertFailure { authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.EDIT.name) }
                .all {
                    hasClass(HankeKayttajaNotFoundException::class)
                    messageContains(kayttajaId.toString())
                }
        }

        @Test
        fun `return true if user has the required permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveUserAndPermission(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertThat(authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.EDIT.name))
                .isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val kayttajaId = UUID.fromString("bb201bf3-58b7-48d5-a7e4-e358370f74e1")

            assertFailure { authorizer.authorizeKayttajaId(kayttajaId, "Not real") }
                .all {
                    hasClass(IllegalArgumentException::class)
                    messageContains(
                        "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                    )
                }
        }
    }
}
