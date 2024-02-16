package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.hasSameElementsAs
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

    @Nested
    inner class HankkeenHakemuksetResponse {
        @Test
        fun `return applications`() {
            val hanke = initHankeWithHakemus()

            val result = hakemusService.hankkeenHakemuksetResponse(hanke.hankeTunnus)

            val expectedHakemus = HankkeenHakemusResponse(applicationRepository.findAll().first())
            assertThat(result.applications).hasSameElementsAs(listOf(expectedHakemus))
        }

        @Test
        fun `when hanke does not exist throws not found`() {
            val hankeTunnus = "HAI-1234"

            assertFailure { hakemusService.hankkeenHakemuksetResponse(hankeTunnus) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `when no applications returns an empty result`() {
            val hankeInitial = hankeFactory.builder(USERNAME).save()

            val result = hakemusService.hankkeenHakemuksetResponse(hankeInitial.hankeTunnus)

            assertThat(result.applications).isEmpty()
        }

        private fun initHankeWithHakemus(): HankeEntity {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = "HAI23-1")
            val application =
                applicationRepository.save(
                    ApplicationFactory.createApplicationEntity(
                        hanke = hanke,
                        alluStatus = ApplicationStatus.PENDING,
                        alluid = null,
                        userId = USERNAME
                    )
                )
            return hanke.apply { hakemukset = mutableSetOf(application) }
        }
    }

    private fun Assert<JohtoselvitysHakemusDataResponse>.hasAllCustomersWithContacts() {
        prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
            .isNotNull()
            .isCompanyCustomerWithOneContact(true)
        prop(JohtoselvitysHakemusDataResponse::contractorWithContacts)
            .isNotNull()
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
