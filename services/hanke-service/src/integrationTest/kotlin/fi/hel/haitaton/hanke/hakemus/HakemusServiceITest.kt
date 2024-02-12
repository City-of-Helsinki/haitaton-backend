package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val USERNAME = "test7358"

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
class HakemusServiceITest : DatabaseTest() {

    @Autowired private lateinit var hakemusService: HakemusService

    @Autowired private lateinit var applicationRepository: ApplicationRepository

    @Autowired private lateinit var applicationFactory: ApplicationFactory
    @Autowired private lateinit var hankeFactory: HankeFactory
    @Autowired private lateinit var hakemusyhteystietoFactory: HakemusyhteystietoFactory
    @Autowired private lateinit var hakemusyhteyshenkiloFactory: HakemusyhteyshenkiloFactory
    @Autowired private lateinit var hankeKayttajaFactory: HankeKayttajaFactory

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
    }

    @Nested
    inner class HakemusResponse {
        @Test
        fun `when application does not exist should throw`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            assertThrows<ApplicationNotFoundException> { hakemusService.hakemusResponse(1234) }
        }

        @Test
        fun `when application exists should return correct response`() {
            val hanke = hankeFactory.saveMinimal()
            val hakijanYhteyshenkilo =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hanke.id,
                    etunimi = "Hannu",
                    sukunimi = "Hakija",
                    sahkoposti = "hannu.hakija@yritys.fi"
                )
            val tyonSuorittajanYhteyshenkilo =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hanke.id,
                    etunimi = "Susanna",
                    sukunimi = "Suorittaja",
                    sahkoposti = "susanna.suorittaja@yritys.fi"
                )
            val application =
                applicationFactory.saveApplicationEntity(
                    USERNAME,
                    hanke = hanke,
                    mutator = { application ->
                        application.yhteystiedot[ApplicationContactType.HAKIJA] =
                            hakemusyhteystietoFactory.createEntity(application = application)
                        application.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA] =
                            hakemusyhteystietoFactory.createEntity(
                                rooli = ApplicationContactType.TYON_SUORITTAJA,
                                application = application
                            )
                    }
                )
            hakemusyhteyshenkiloFactory.saveHakemusyhteyshenkilo(
                application.yhteystiedot[ApplicationContactType.HAKIJA]!!,
                hakijanYhteyshenkilo,
                true
            )
            hakemusyhteyshenkiloFactory.saveHakemusyhteyshenkilo(
                application.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]!!,
                tyonSuorittajanYhteyshenkilo
            )

            val response = hakemusService.hakemusResponse(application.id!!)

            assertThat(response.id).isEqualTo(application.id)
            val applicationData = response.applicationData as JohtoselvitysHakemusDataResponse
            val hakija = application.yhteystiedot[ApplicationContactType.HAKIJA]!!
            val tyonSuorittaja = application.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]!!
            assertThat(applicationData.customerWithContacts.customer).all {
                prop(CustomerResponse::yhteystietoId).isEqualTo(hakija.id)
                prop(CustomerResponse::type).isEqualTo(hakija.tyyppi)
                prop(CustomerResponse::name).isEqualTo(hakija.nimi)
            }
            assertThat(applicationData.customerWithContacts.contacts).single().all {
                prop(ContactResponse::hankekayttajaId).isEqualTo(hakijanYhteyshenkilo.id)
                prop(ContactResponse::tilaaja).isTrue()
            }
            assertThat(applicationData.contractorWithContacts.customer).all {
                prop(CustomerResponse::yhteystietoId).isEqualTo(tyonSuorittaja.id)
                prop(CustomerResponse::type).isEqualTo(tyonSuorittaja.tyyppi)
                prop(CustomerResponse::name).isEqualTo(tyonSuorittaja.nimi)
            }
            assertThat(applicationData.contractorWithContacts.contacts).single().all {
                prop(ContactResponse::hankekayttajaId).isEqualTo(tyonSuorittajanYhteyshenkilo.id)
                prop(ContactResponse::tilaaja).isFalse()
            }
        }
    }
}
