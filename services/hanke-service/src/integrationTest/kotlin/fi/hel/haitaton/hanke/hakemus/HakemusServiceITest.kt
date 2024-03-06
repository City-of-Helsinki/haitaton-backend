package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.permissions.HankeKayttajaNotFoundException
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import java.util.UUID
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
class HakemusServiceITest(
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hankeKayttajaService: HankeKayttajaService,
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
) : DatabaseTest() {

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

    @Nested
    inner class HankkeenHakemuksetResponse {
        @Test
        fun `returns applications`() {
            val (_, hanke) = hankeFactory.builder(USERNAME).saveAsGenerated()

            val result = hakemusService.hankkeenHakemuksetResponse(hanke.hankeTunnus)

            val expectedHakemus = HankkeenHakemusResponse(applicationRepository.findAll().first())
            assertThat(result.applications).hasSameElementsAs(listOf(expectedHakemus))
        }

        @Test
        fun `throws not found when hanke does not exist`() {
            val hankeTunnus = "HAI-1234"

            assertFailure { hakemusService.hankkeenHakemuksetResponse(hankeTunnus) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `returns an empty result when there are no applications`() {
            val hankeInitial = hankeFactory.builder(USERNAME).save()

            val result = hakemusService.hankkeenHakemuksetResponse(hankeInitial.hankeTunnus)

            assertThat(result.applications).isEmpty()
        }
    }

    @Nested
    inner class GetHakemuksetForKayttaja {
        @Test
        fun `throws an exception when kayttaja does not exist`() {
            val kayttajaId = UUID.fromString("750b4207-2c5f-49a0-9b80-4829c807abeb")

            val failure = assertFailure { hakemusService.getHakemuksetForKayttaja(kayttajaId) }

            failure.all {
                hasClass(HankeKayttajaNotFoundException::class)
                messageContains(kayttajaId.toString())
            }
        }

        @Test
        fun `returns an empty list when the kayttaja exists but there are no applications`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!

            val result = hakemusService.getHakemuksetForKayttaja(founder.id)

            assertThat(result).isEmpty()
        }

        @Test
        fun `returns applications when the kayttaja is an yhteyshenkilo`() {
            val hanke = hankeFactory.saveWithAlue(USERNAME)
            val founder = hankeKayttajaService.getKayttajaByUserId(hanke.id, USERNAME)!!
            val application1 =
                hakemusFactory.builder(USERNAME, hanke).saveWithYhteystiedot {
                    hakija { addYhteyshenkilo(it, founder) }
                    rakennuttaja()
                    tyonSuorittaja()
                }
            val application2 =
                hakemusFactory.builder(USERNAME, hanke).saveWithYhteystiedot {
                    hakija()
                    rakennuttaja()
                    tyonSuorittaja { addYhteyshenkilo(it, founder) }
                }

            val result = hakemusService.getHakemuksetForKayttaja(founder.id)

            assertThat(result)
                .extracting { it.id }
                .containsExactlyInAnyOrder(application1.id, application2.id)
        }
    }
}
