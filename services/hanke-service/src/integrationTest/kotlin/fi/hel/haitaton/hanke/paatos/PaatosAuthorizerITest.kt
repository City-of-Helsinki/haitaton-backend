package fi.hel.haitaton.hanke.paatos

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.HankeAlreadyCompletedException
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso.KATSELUOIKEUS
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT_APPLICATIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.test.USERNAME
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PaatosAuthorizerITest(
    @Autowired private val authorizer: PaatosAuthorizer,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val paatosFactory: PaatosFactory,
) : IntegrationTest() {

    private val paatosId = UUID.fromString("871565c5-cdbb-4d29-9d63-68467f5bf9d6")

    @Nested
    inner class AuthorizePaatosId {
        @Test
        fun `throws exception if paatos doesn't exist`() {
            val failure = assertFailure { authorizer.authorizePaatosId(paatosId, VIEW.name) }

            failure.all {
                hasClass(PaatosNotFoundException::class)
                messageContains(paatosId.toString())
            }
        }

        @Test
        fun `throws exception if user doesn't have a permission for the hanke`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").asSent().save()
            val paatos = paatosFactory.save(hakemus)

            val failure = assertFailure { authorizer.authorizePaatosId(paatos.id, VIEW.name) }

            failure.all {
                hasClass(PaatosNotFoundException::class)
                messageContains(paatos.id.toString())
            }
        }

        @Test
        fun `throws exception if user doesn't have the required permission`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").asSent().save()
            val paatos = paatosFactory.save(hakemus)
            hankeKayttajaFactory.saveIdentifiedUser(
                hakemus.hankeId,
                userId = USERNAME,
                kayttooikeustaso = KATSELUOIKEUS,
            )

            val failure = assertFailure {
                authorizer.authorizePaatosId(paatos.id, EDIT_APPLICATIONS.name)
            }

            failure.all {
                hasClass(PaatosNotFoundException::class)
                messageContains(paatos.id.toString())
            }
        }

        @Test
        fun `returns true if user has the required permission`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").asSent().save()
            val paatos = paatosFactory.save(hakemus)
            hankeKayttajaFactory.saveIdentifiedUser(
                hakemus.hankeId,
                userId = USERNAME,
                kayttooikeustaso = KATSELUOIKEUS,
            )

            val result = authorizer.authorizePaatosId(paatos.id, VIEW.name)

            assertThat(result).isTrue()
        }

        @Test
        fun `throws exception when modifying a paatos from a completed hanke`() {
            val hanke = hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)
            val hakemus = hakemusFactory.builder(hankeEntity = hanke).asSent().save()
            val paatos = paatosFactory.save(hakemus)

            val failure = assertFailure {
                authorizer.authorizePaatosId(paatos.id, EDIT_APPLICATIONS.name)
            }

            failure.all {
                hasClass(HankeAlreadyCompletedException::class)
                messageContains("Hanke has already been completed, so the operation is not allowed")
                messageContains("hankeId=${hanke.id}")
            }
        }

        @Test
        fun `returns true when viewing a paatos from a completed hanke`() {
            val hanke = hankeFactory.builder().saveEntity(HankeStatus.COMPLETED)
            val hakemus = hakemusFactory.builder(hankeEntity = hanke).asSent().save()
            val paatos = paatosFactory.save(hakemus)

            val result = authorizer.authorizePaatosId(paatos.id, VIEW.name)

            assertThat(result).isTrue()
        }
    }
}
