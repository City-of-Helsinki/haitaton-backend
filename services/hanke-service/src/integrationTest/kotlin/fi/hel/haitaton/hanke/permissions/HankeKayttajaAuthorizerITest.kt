package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeAlreadyCompletedException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HankeKayttajaAuthorizerITest(
    @Autowired private val authorizer: HankeKayttajaAuthorizer,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val permissionService: PermissionService,
) : IntegrationTest() {

    @Nested
    inner class AuthorizeKayttajaId {
        @Test
        fun `throws exception if kayttajaId is not found`() {
            val kayttajaId = UUID.fromString("bb201bf3-58b7-48d5-a7e4-e358370f74e1")

            val failure = assertFailure {
                authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.VIEW.name)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveUser(hankeId).id

            val failure = assertFailure {
                authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.VIEW.name)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            val failure = assertFailure {
                authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.EDIT.name)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `return true if user has the required permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            val result = authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.EDIT.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val kayttajaId = UUID.fromString("bb201bf3-58b7-48d5-a7e4-e358370f74e1")

            val failure = assertFailure { authorizer.authorizeKayttajaId(kayttajaId, "Not real") }

            failure.all {
                hasClass(IllegalArgumentException::class)
                messageContains(
                    "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                )
            }
        }

        @Test
        fun `throws exception when modifying a hankekayttaja from a completed hanke`() {
            val hankeId = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED).id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val failure = assertFailure {
                authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.EDIT.name)
            }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=${hankeId}")
            }
        }

        @Test
        fun `returns true when viewing a hankekayttaja from a completed hanke`() {
            val hankeId = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED).id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val result = authorizer.authorizeKayttajaId(kayttajaId, PermissionCode.VIEW.name)

            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class AuthorizeResend {
        @Test
        fun `throws exception if kayttajaId is not found`() {
            val kayttajaId = UUID.fromString("bb201bf3-58b7-48d5-a7e4-e358370f74e1")

            val failure = assertFailure {
                authorizer.authorizeResend(kayttajaId, PermissionCode.VIEW.name)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `throws exception if user doesn't have any permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveUser(hankeId).id

            val failure = assertFailure {
                authorizer.authorizeResend(kayttajaId, PermissionCode.VIEW.name)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `throws exception if user doesn't have the required permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KATSELUOIKEUS)

            val failure = assertFailure {
                authorizer.authorizeResend(kayttajaId, PermissionCode.EDIT.name)
            }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `return true if user has the required permission for the hanke`() {
            val hankeId = hankeFactory.saveMinimal().id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.HANKEMUOKKAUS)

            val result = authorizer.authorizeResend(kayttajaId, PermissionCode.EDIT.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws error if enum value not found`() {
            val kayttajaId = UUID.fromString("bb201bf3-58b7-48d5-a7e4-e358370f74e1")

            val failure = assertFailure { authorizer.authorizeResend(kayttajaId, "Not real") }

            failure.all {
                hasClass(IllegalArgumentException::class)
                messageContains(
                    "No enum constant fi.hel.haitaton.hanke.permissions.PermissionCode.Not real"
                )
            }
        }

        @Test
        fun `returns true when modifying a hankekayttaja from a completed hanke`() {
            val hankeId = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED).id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val result = authorizer.authorizeResend(kayttajaId, PermissionCode.EDIT.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `returns true when viewing a hankekayttaja from a completed hanke`() {
            val hankeId = hankeFactory.saveMinimal(status = HankeStatus.COMPLETED).id
            val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hankeId).id
            permissionService.create(hankeId, USERNAME, Kayttooikeustaso.KAIKKI_OIKEUDET)

            val result = authorizer.authorizeResend(kayttajaId, PermissionCode.VIEW.name)

            assertThat(result).isTrue()
        }
    }
}
