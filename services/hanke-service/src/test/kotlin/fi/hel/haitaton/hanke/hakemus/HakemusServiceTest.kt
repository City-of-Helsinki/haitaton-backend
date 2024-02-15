package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val USERNAME = "test"

class HakemusServiceTest {
    private val applicationRepository: ApplicationRepository = mockk()

    private val hakemusService: HakemusService = HakemusService(applicationRepository)

    companion object {
        private val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()
    }

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun verifyMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            applicationRepository,
        )
    }

    @Nested
    inner class HakemusResponse {
        @Test
        fun `when invalid application id throws exception`() {
            every { applicationRepository.findOneById(1234L) } returns null

            assertThrows<ApplicationNotFoundException> { hakemusService.hakemusResponse(1234L) }

            verify { applicationRepository.findOneById(1234L) }
        }

        @Test
        fun `return application response with contact information referencing correct hanke users`() {
            val applicationEntity = applicationEntity()
            val hakija = applicationEntity.yhteystiedot[ApplicationContactType.HAKIJA]!!
            val tyonSuorittaja =
                applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]!!
            every { applicationRepository.findOneById(applicationEntity.id!!) } returns
                applicationEntity

            val hakemusResponse = hakemusService.hakemusResponse(applicationEntity.id!!)

            assertThat(hakemusResponse.id).isEqualTo(applicationEntity.id)
            assertThat(hakemusResponse.alluid).isEqualTo(null)
            val hakemusDataResponse =
                hakemusResponse.applicationData as JohtoselvitysHakemusDataResponse
            assertThat(hakemusDataResponse.customerWithContacts)
                .isNotNull()
                .hasCorrectCustomerAndContact(hakija, true)
            assertThat(hakemusDataResponse.contractorWithContacts)
                .isNotNull()
                .hasCorrectCustomerAndContact(tyonSuorittaja, false)
            verifySequence { applicationRepository.findOneById(applicationEntity.id!!) }
        }
    }

    private fun applicationEntity(
        id: Long? = 3,
        alluId: Int? = null,
        data: ApplicationData = applicationData,
        hanke: HankeEntity = HankeFactory.createMinimalEntity(id = 1)
    ) =
        ApplicationFactory.createApplicationEntity(
                id = id,
                alluid = alluId,
                userId = USERNAME,
                applicationData = data,
                hanke = hanke,
            )
            .apply {
                yhteystiedot[ApplicationContactType.HAKIJA] =
                    HakemusyhteystietoFactory.createEntity(
                            rooli = ApplicationContactType.HAKIJA,
                            application = this
                        )
                        .withYhteyshenkilo(HankeKayttajaFactory.createEntity(), tilaaja = true)
                yhteystiedot[ApplicationContactType.TYON_SUORITTAJA] =
                    HakemusyhteystietoFactory.createEntity(
                            rooli = ApplicationContactType.TYON_SUORITTAJA,
                            application = this
                        )
                        .withYhteyshenkilo(HankeKayttajaFactory.createEntity())
            }

    private fun Assert<CustomerWithContactsResponse>.hasCorrectCustomerAndContact(
        hakemusyhteystieto: HakemusyhteystietoEntity,
        orderer: Boolean
    ) {
        prop(CustomerWithContactsResponse::customer).all {
            prop(CustomerResponse::yhteystietoId).isEqualTo(hakemusyhteystieto.id)
            prop(CustomerResponse::type).isEqualTo(hakemusyhteystieto.tyyppi)
            prop(CustomerResponse::name).isEqualTo(hakemusyhteystieto.nimi)
        }
        prop(CustomerWithContactsResponse::contacts).single().all {
            prop(ContactResponse::hankekayttajaId)
                .isEqualTo(hakemusyhteystieto.yhteyshenkilot.single().hankekayttaja.id)
            prop(ContactResponse::orderer).isEqualTo(orderer)
        }
    }
}
