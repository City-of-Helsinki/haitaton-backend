package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasSize
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
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.toJsonString
import java.util.UUID
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

private const val USERID = "test-user"

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERID)
class HakemusServiceITest : DatabaseTest() {

    @Autowired private lateinit var hakemusService: HakemusService

    @Autowired private lateinit var applicationRepository: ApplicationRepository

    @Autowired private lateinit var hakemusyhteystietoRepository: HakemusyhteystietoRepository

    @Autowired private lateinit var hankekayttajaRepository: HankekayttajaRepository

    @Autowired private lateinit var auditLogRepository: AuditLogRepository

    @Autowired private lateinit var hakemusFactory: HakemusFactory

    @Autowired private lateinit var hankeFactory: HankeFactory

    @Autowired private lateinit var hankeKayttajaFactory: HankeKayttajaFactory

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
                hakemusFactory.builder(USERID, hanke).saveWithYhteystiedot {
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
            val hankeInitial = hankeFactory.builder(USERID).save()

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
                        userId = USERID
                    )
                )
            return hanke.apply { hakemukset = mutableSetOf(application) }
        }
    }

    @Nested
    open inner class UpdateHakemus {

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

            assertThrows<ApplicationNotFoundException> {
                hakemusService.updateHakemus(
                    1234,
                    HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest(),
                    USERID
                )
            }
        }

        @Test
        fun `throws exception when the application has been sent to Allu`() {
            val application = createHakemus { alluid = 21 }

            val exception =
                assertThrows<ApplicationAlreadySentException> {
                    hakemusService.updateHakemus(
                        application.id!!,
                        HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest(),
                        USERID
                    )
                }

            assertThat(exception.message)
                .isEqualTo("Application is already sent to Allu, id=${application.id}, alluid=21")
        }

        @Test
        fun `does not create a new audit log entry when the application has not changed`() {
            val application = createHakemus()
            val originalAuditLogSize = auditLogRepository.findByType(ObjectType.APPLICATION).size

            hakemusService.updateHakemus(
                application.id!!,
                HakemusUpdateRequestFactory
                    .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(application),
                USERID
            )

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(originalAuditLogSize)
        }

        @Test
        fun `throws exception when there are invalid geometry in areas`() {
            val application = createHakemus()

            val exception =
                assertThrows<ApplicationGeometryException> {
                    hakemusService.updateHakemus(
                        application.id!!,
                        HakemusUpdateRequestFactory
                            .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(
                                application
                            )
                            .copy(areas = listOf(intersectingArea)),
                        USERID
                    )
                }

            Assertions.assertEquals(
                """Invalid geometry received when updating application id=${application.id},
                | reason = Self-intersection,
                | location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                    .trimMargin()
                    .replace("\n", ""),
                exception.message
            )
        }

        @Test
        fun `throws exception when the request has a persisted contact but the application does not`() {
            val application = createHakemus()
            val requestYhteystietoId = UUID.randomUUID()

            val exception =
                assertThrows<InvalidHakemusyhteystietoException> {
                    hakemusService.updateHakemus(
                        application.id!!,
                        HakemusUpdateRequestFactory
                            .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(
                                application
                            )
                            .copy(
                                customerWithContacts =
                                    HakemusUpdateRequestFactory.createCustomerWithContactsRequest(
                                        yhteystietoId = requestYhteystietoId
                                    )
                            ),
                        USERID
                    )
                }

            assertThat(exception.message)
                .isEqualTo(
                    "Invalid hakemusyhteystieto received when updating application id=${application.id}, role=HAKIJA, yhteystietoId=null, newId=$requestYhteystietoId"
                )
        }

        @Test
        fun `throws exception when the request has different persisted contact than the application`() {
            val application = createHakemusWithHakija()
            val originalYhteystietoId = hakemusyhteystietoRepository.findAll().first().id
            val requestYhteystietoId = UUID.randomUUID()

            val exception =
                assertThrows<InvalidHakemusyhteystietoException> {
                    hakemusService.updateHakemus(
                        application.id!!,
                        HakemusUpdateRequestFactory
                            .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(
                                application
                            )
                            .copy(
                                customerWithContacts =
                                    HakemusUpdateRequestFactory.createCustomerWithContactsRequest(
                                        yhteystietoId = requestYhteystietoId
                                    )
                            ),
                        USERID
                    )
                }

            assertThat(exception.message)
                .isEqualTo(
                    "Invalid hakemusyhteystieto received when updating application id=${application.id}, role=HAKIJA, yhteystietoId=$originalYhteystietoId, newId=$requestYhteystietoId"
                )
        }

        @Test
        fun `throws exception when the request has a contact that is not a user on hanke`() {
            val application = createHakemusWithHakija()
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val requestHankekayttajaId = UUID.randomUUID()

            val exception =
                assertThrows<InvalidHakemusyhteyshenkiloException> {
                    hakemusService.updateHakemus(
                        application.id!!,
                        HakemusUpdateRequestFactory
                            .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(
                                application
                            )
                            .copy(
                                customerWithContacts =
                                    HakemusUpdateRequestFactory.createCustomerWithContactsRequest(
                                        CustomerType.COMPANY,
                                        yhteystieto.id,
                                        requestHankekayttajaId
                                    )
                            ),
                        USERID
                    )
                }

            assertThat(exception.message)
                .isEqualTo(
                    "Invalid hanke user/users received when updating application id=${application.id}, invalid hankeKayttajaIds=[$requestHankekayttajaId]"
                )
        }

        @Test
        fun `throws exception when area is not inside hanke area`() {
            val hanke = hankeFactory.builder(USERID).withHankealue().saveEntity()
            val application = createHakemus(hanke)

            val exception =
                assertThrows<ApplicationGeometryNotInsideHankeException> {
                    hakemusService.updateHakemus(
                        application.id!!,
                        HakemusUpdateRequestFactory
                            .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(
                                application
                            )
                            .copy(areas = listOf(notInHankeArea)),
                        USERID
                    )
                }

            Assertions.assertEquals(
                """Application geometry doesn't match any hankealue when updating application id=${application.id},
                | hankeId = ${hanke.id},
                | application geometry = ${notInHankeArea.geometry.toJsonString()}"""
                    .trimMargin()
                    .replace("\n", ""),
                exception.message
            )
        }

        @Test
        fun `saves updated data and creates an audit log`() {
            val hanke = hankeFactory.builder(USERID).withHankealue().saveEntity()
            val application =
                createHakemusWithHakija(hanke) {
                    applicationData =
                        (applicationData as CableReportApplicationData).copy(
                            workDescription = "Old work description"
                        )
                }
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val kayttaja =
                hankekayttajaRepository
                    .findByHankeIdAndSahkopostiIn(hanke.id, listOf(KAYTTAJA_INPUT_HAKIJA.email))
                    .single()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val originalAuditLogSize = auditLogRepository.findByType(ObjectType.APPLICATION).size

            val request =
                HakemusUpdateRequestFactory
                    .createJohtoselvityshakemusUpdateRequestFromApplicationEntity(application)
                    .copy(
                        // change work description
                        workDescription = "New work description",
                        // add a new contact
                        customerWithContacts =
                            HakemusUpdateRequestFactory.createCustomerWithContactsRequest(
                                CustomerType.COMPANY,
                                yhteystieto.id,
                                kayttaja.id,
                                newKayttaja.id
                            )
                    )
            val updatedHakemus = hakemusService.updateHakemus(application.id!!, request, USERID)

            assertThat(
                    (updatedHakemus.applicationData as JohtoselvitysHakemusDataResponse)
                        .workDescription
                )
                .isEqualTo("New work description")
            assertThat(
                    (updatedHakemus.applicationData as JohtoselvitysHakemusDataResponse)
                        .customerWithContacts!!
                        .contacts
                )
                .hasSize(2)

            val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
            assertThat(applicationLogs).hasSize(originalAuditLogSize + 1)
        }

        private fun createHakemus(
            hankeEntity: HankeEntity = hankeFactory.builder(USERID).withHankealue().saveEntity(),
            f: ApplicationEntity.() -> Unit = {}
        ): ApplicationEntity {
            return hakemusFactory.builder(USERID, hankeEntity).withModification(f).save()
        }

        private fun createHakemusWithHakija(
            hankeEntity: HankeEntity = hankeFactory.builder(USERID).withHankealue().saveEntity(),
            f: ApplicationEntity.() -> Unit = {}
        ): ApplicationEntity {
            return hakemusFactory
                .builder(USERID, hankeEntity)
                .withModification(f)
                .saveWithYhteystiedot { hakija() }
        }
    }
}
