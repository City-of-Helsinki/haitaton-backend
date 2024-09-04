package fi.hel.haitaton.hanke.paatos

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.factory.HakemusFactory
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
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val paatosFactory: PaatosFactory,
) : IntegrationTest() {

    val paatosId = UUID.fromString("871565c5-cdbb-4d29-9d63-68467f5bf9d6")

    @Nested
    inner class AuthorizePaatosId {
        @Test
        fun `throws exception if paatos doesn't exist`() {
            assertFailure { authorizer.authorizePaatosId(paatosId, VIEW.name) }
                .all {
                    hasClass(PaatosNotFoundException::class)
                    messageContains(paatosId.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have a permission for the hanke`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").asSent().save()
            val paatos = paatosFactory.save(hakemus)

            assertFailure { authorizer.authorizePaatosId(paatos.id, VIEW.name) }
                .all {
                    hasClass(PaatosNotFoundException::class)
                    messageContains(paatos.id.toString())
                }
        }

        @Test
        fun `throws exception if user doesn't have the required permission`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").asSent().save()
            val paatos = paatosFactory.save(hakemus)
            hankeKayttajaFactory.saveIdentifiedUser(
                hakemus.hankeId, userId = USERNAME, kayttooikeustaso = KATSELUOIKEUS)

            assertFailure { authorizer.authorizePaatosId(paatos.id, EDIT_APPLICATIONS.name) }
                .all {
                    hasClass(PaatosNotFoundException::class)
                    messageContains(paatos.id.toString())
                }
        }

        @Test
        fun `returns true if user has the required permission`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").asSent().save()
            val paatos = paatosFactory.save(hakemus)
            hankeKayttajaFactory.saveIdentifiedUser(
                hakemus.hankeId, userId = USERNAME, kayttooikeustaso = KATSELUOIKEUS)

            assertThat(authorizer.authorizePaatosId(paatos.id, VIEW.name)).isTrue()
        }
    }
}
