package fi.hel.haitaton.hanke.application

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
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
class ApplicationAuthorizerITest(
    @Autowired private val authorizer: ApplicationAuthorizer,
    @Autowired private val alluDataFactory: AlluDataFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val permissionService: PermissionService,
) : DatabaseTest() {

    @Nested
    inner class AuthorizeApplicationId {
        @Test
        fun `throws exception if application doesn't exist`() {
            val applicationId = 1L

            assertFailure {
                    authorizer.authorizeApplicationId(applicationId, PermissionCode.VIEW.name)
                }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(applicationId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have a permission for the hanke`() {
            val hanke = hankeFactory.saveMinimal()
            val application = alluDataFactory.saveApplicationEntity(USERNAME, hanke)

            assertFailure {
                    authorizer.authorizeApplicationId(application.id!!, PermissionCode.VIEW.name)
                }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission`() {
            val hanke = hankeFactory.saveMinimal()
            val application = alluDataFactory.saveApplicationEntity(USERNAME, hanke)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure {
                    authorizer.authorizeApplicationId(application.id!!, PermissionCode.DELETE.name)
                }
                .all {
                    hasClass(ApplicationNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `returns true if user has the required permission`() {
            val hanke = hankeFactory.saveMinimal()
            val application = alluDataFactory.saveApplicationEntity(USERNAME, hanke)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertThat(
                    authorizer.authorizeApplicationId(application.id!!, PermissionCode.VIEW.name)
                )
                .isTrue()
        }
    }

    @Nested
    inner class AuthorizeCreate {
        private val hankeTunnus = "HAI23-1414"

        @Test
        fun `throws exception if hanketunnus not found`() {
            val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)

            assertFailure { authorizer.authorizeCreate(application) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have EDIT_APPLICATIONS permission`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            assertFailure { authorizer.authorizeCreate(application) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(application.id.toString())
                }
        }

        @Test
        fun `returns true if user has EDIT_APPLICATIONS permission`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)
            val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)
            permissionService.create(hanke.id, USERNAME, Kayttooikeustaso.HAKEMUSASIOINTI)

            assertThat(authorizer.authorizeCreate(application)).isTrue()
        }
    }
}
