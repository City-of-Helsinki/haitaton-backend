package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.toUpdateRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withAreas
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomerWithContactsRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.permissions.HankeKayttajaNotFoundException
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.toJsonString
import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
    @Autowired private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory
) : DatabaseTest() {

    @Nested
    inner class HakemusResponse {
        @Test
        fun `when application does not exist should throw`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            val exception = assertFailure { hakemusService.hakemusResponse(1234) }

            exception.hasClass(ApplicationNotFoundException::class)
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

    @Nested
    inner class UpdateHakemus {

        private val intersectingArea =
            ApplicationFactory.createApplicationArea(
                name = "area",
                geometry =
                    "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json".asJsonResource()
            )

        private val notInHankeArea =
            ApplicationFactory.createApplicationArea(
                name = "area",
                geometry = GeometriaFactory.polygon
            )

        @Test
        fun `throws exception when the application does not exist`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()

            val exception = assertFailure { hakemusService.updateHakemus(1234, request, USERNAME) }

            exception.hasClass(ApplicationNotFoundException::class)
        }

        @Test
        fun `throws exception when the application has been sent to Allu`() {
            val entity = hakemusFactory.builder(USERNAME).withStatus(alluId = 21).save()
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val request = hakemus.toUpdateRequest()

            val exception = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            exception.all {
                hasClass(ApplicationAlreadySentException::class)
                messageContains("id=${hakemus.id}")
                messageContains("alluid=21")
            }
        }

        @Test
        fun `does not create a new audit log entry when the application has not changed`() {
            val entity = hakemusFactory.builder(USERNAME).save()
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val originalAuditLogSize = auditLogRepository.findByType(ObjectType.APPLICATION).size
            val request = hakemus.toUpdateRequest()

            hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(originalAuditLogSize)
        }

        @Test
        fun `throws exception when there are invalid geometry in areas`() {
            val entity = hakemusFactory.builder(USERNAME).save()
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val request = hakemus.toUpdateRequest().withAreas(listOf(intersectingArea))

            val exception = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            exception.all {
                hasClass(ApplicationGeometryException::class)
                messageContains("id=${hakemus.id}")
                messageContains("reason=Self-intersection")
                messageContains(
                    "location={\"type\":\"Point\",\"coordinates\":[25494009.65639264,6679886.142116806]}"
                )
            }
        }

        @Test
        fun `throws exception when the request has a persisted contact but the application does not`() {
            val entity = hakemusFactory.builder(USERNAME).save()
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val requestYhteystietoId = UUID.randomUUID()
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(CustomerType.COMPANY, requestYhteystietoId)

            val exception = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            exception.all {
                hasClass(InvalidHakemusyhteystietoException::class)
                messageContains("id=${hakemus.id}")
                messageContains("role=${ApplicationContactType.HAKIJA}")
                messageContains("yhteystietoId=null")
                messageContains("newId=$requestYhteystietoId")
            }
        }

        @Test
        fun `throws exception when the request has different persisted contact than the application`() {
            val entity = hakemusFactory.builder(USERNAME).saveWithYhteystiedot { hakija() }
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val originalYhteystietoId = hakemusyhteystietoRepository.findAll().first().id
            val requestYhteystietoId = UUID.randomUUID()
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(CustomerType.COMPANY, requestYhteystietoId)

            val exception = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            exception.all {
                hasClass(InvalidHakemusyhteystietoException::class)
                messageContains("id=${hakemus.id}")
                messageContains("role=${ApplicationContactType.HAKIJA}")
                messageContains("yhteystietoId=$originalYhteystietoId")
                messageContains("newId=$requestYhteystietoId")
            }
        }

        @Test
        fun `throws exception when the request has a contact that is not a user on hanke`() {
            val entity = hakemusFactory.builder(USERNAME).saveWithYhteystiedot { hakija() }
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val requestHankekayttajaId = UUID.randomUUID()
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(
                        CustomerType.COMPANY,
                        yhteystieto.id,
                        requestHankekayttajaId
                    )

            val exception = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            exception.all {
                hasClass(InvalidHakemusyhteyshenkiloException::class)
                messageContains("id=${hakemus.id}")
                messageContains("invalidHankeKayttajaIds=[$requestHankekayttajaId]")
            }
        }

        @Test
        fun `throws exception when area is not inside hanke area`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val entity = hakemusFactory.builder(USERNAME, hanke).save()
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val request = hakemus.toUpdateRequest().withAreas(listOf(notInHankeArea))

            val exception = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            exception.all {
                hasClass(ApplicationGeometryNotInsideHankeException::class)
                messageContains("id=${hakemus.id}")
                messageContains("hankeId=${hanke.id}")
                messageContains("geometry=${notInHankeArea.geometry.toJsonString()}")
            }
        }

        @Test
        fun `saves updated data and creates an audit log`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val entity =
                hakemusFactory
                    .builder(USERNAME, hanke)
                    .withWorkDescription("Old work description")
                    .saveWithYhteystiedot { hakija() }
            val hakemus = hakemusService.hakemusResponse(entity.id!!)
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val kayttaja =
                hankekayttajaRepository
                    .findByHankeIdAndSahkopostiIn(hanke.id, listOf(KAYTTAJA_INPUT_HAKIJA.email))
                    .single()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val originalAuditLogSize = auditLogRepository.findByType(ObjectType.APPLICATION).size
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(
                        CustomerType.COMPANY,
                        yhteystieto.id,
                        kayttaja.id,
                        newKayttaja.id
                    )
                    .withWorkDescription("New work description")

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData as JohtoselvitysHakemusDataResponse).all {
                prop(JohtoselvitysHakemusDataResponse::workDescription)
                    .isEqualTo("New work description")
                prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .transform { it.map { contact -> contact.hankekayttajaId } }
                    .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
            }
            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(originalAuditLogSize + 1)
        }
    }
}
