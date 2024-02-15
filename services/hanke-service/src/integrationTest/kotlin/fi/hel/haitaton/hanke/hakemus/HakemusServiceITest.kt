package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val USERNAME = "test7358"

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HakemusServiceITest : DatabaseTest() {

    @Autowired private lateinit var hakemusService: HakemusService

    @Autowired private lateinit var applicationRepository: ApplicationRepository

    @Autowired private lateinit var hakemusFactory: HakemusFactory

    @Autowired private lateinit var hankeFactory: HankeFactory

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
        prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
            .isCompanyCustomerWithOneContact(true)
        prop(JohtoselvitysHakemusDataResponse::contractorWithContacts)
            .isCompanyCustomerWithOneContact(false)
        prop(JohtoselvitysHakemusDataResponse::propertyDeveloperWithContacts)
            .isNotNull()
            .isCompanyCustomerWithOneContact(false)
        prop(JohtoselvitysHakemusDataResponse::representativeWithContacts)
            .isNotNull()
            .isCompanyCustomerWithOneContact(false)
    }

    private fun Assert<CustomerWithContactsResponse>.isCompanyCustomerWithOneContact(
        orderer: Boolean
    ) {
        prop(CustomerWithContactsResponse::customer)
            .prop(CustomerResponse::type)
            .isEqualTo(CustomerType.COMPANY)

        prop(CustomerWithContactsResponse::contacts)
            .single()
            .prop(ContactResponse::orderer)
            .isEqualTo(orderer)
    }
}
