package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
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

    @Autowired private lateinit var hakemusFactory: HakemusFactory
    @Autowired private lateinit var hankeFactory: HankeFactory

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
        fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val application =
                hakemusFactory.builder(USERNAME, hanke).saveWithYhteystiedot {
                    hakija(kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET)
                    tyonSuorittaja(kayttooikeustaso = Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    rakennuttaja(kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI)
                    asianhoitaja()
                }

            val response = hakemusService.hakemusResponse(application.id!!)

            assertThat(response.applicationData as JohtoselvitysHakemusDataResponse)
                .hasAllCustomersWithContacts()
        }
    }

    private fun Assert<JohtoselvitysHakemusDataResponse>.hasAllCustomersWithContacts() {
        prop(JohtoselvitysHakemusDataResponse::customerWithContacts).all {
            prop(CustomerWithContactsResponse::customer).all {
                prop(CustomerResponse::type).isEqualTo(CustomerType.COMPANY)
            }
        }
        prop(JohtoselvitysHakemusDataResponse::customerWithContacts).all {
            prop(CustomerWithContactsResponse::contacts).single().all {
                prop(ContactResponse::orderer).isTrue()
            }
        }
        prop(JohtoselvitysHakemusDataResponse::contractorWithContacts).all {
            prop(CustomerWithContactsResponse::customer).all {
                prop(CustomerResponse::type).isEqualTo(CustomerType.COMPANY)
            }
        }
        prop(JohtoselvitysHakemusDataResponse::contractorWithContacts).all {
            prop(CustomerWithContactsResponse::contacts).single().all {
                prop(ContactResponse::orderer).isFalse()
            }
        }
        prop(JohtoselvitysHakemusDataResponse::propertyDeveloperWithContacts).isNotNull().all {
            prop(CustomerWithContactsResponse::customer).all {
                prop(CustomerResponse::type).isEqualTo(CustomerType.COMPANY)
            }
        }
        prop(JohtoselvitysHakemusDataResponse::propertyDeveloperWithContacts).isNotNull().all {
            prop(CustomerWithContactsResponse::contacts).single().all {
                prop(ContactResponse::orderer).isFalse()
            }
        }
        prop(JohtoselvitysHakemusDataResponse::representativeWithContacts).isNotNull().all {
            prop(CustomerWithContactsResponse::customer).all {
                prop(CustomerResponse::type).isEqualTo(CustomerType.COMPANY)
            }
        }
        prop(JohtoselvitysHakemusDataResponse::representativeWithContacts).isNotNull().all {
            prop(CustomerWithContactsResponse::contacts).single().all {
                prop(ContactResponse::orderer).isFalse()
            }
        }
    }
}
