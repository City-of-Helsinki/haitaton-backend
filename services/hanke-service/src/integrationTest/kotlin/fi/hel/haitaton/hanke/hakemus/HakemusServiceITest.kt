package fi.hel.haitaton.hanke.hakemus

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.ALLU_USER_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.ApplicationAlreadyProcessingException
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType.ASIANHOITAJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationDecisionNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationDeletionResultDto
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.asUtc
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.toUpdateRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withArea
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withAreas
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withContractor
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomer
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomerWithContactsRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withName
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AlluContactWithRole
import fi.hel.haitaton.hanke.logging.AlluCustomerWithRole
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.test.AlluException
import fi.hel.haitaton.hanke.test.Asserts.hasStreetName
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasServiceActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.springframework.beans.factory.annotation.Autowired

class HakemusServiceITest(
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    @Autowired private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val applicationAttachmentRepository: ApplicationAttachmentRepository,
    @Autowired private val geometriatDao: GeometriatDao,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val alluClient: CableReportService,
    @Autowired private val alluStatusRepository: AlluStatusRepository,
) : IntegrationTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluClient)
    }

    @Nested
    inner class GetById {
        @Test
        fun `throws an exception when the application does not exist`() {
            val failure = assertFailure { hakemusService.getById(1234) }

            failure.all {
                hasClass(ApplicationNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
            val application =
                hakemusFactory
                    .builder(USERNAME)
                    .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET, tilaaja = true)
                    .rakennuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                    .asianhoitaja()
                    .tyonSuorittaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    .save()

            val response = hakemusService.getById(application.id)

            assertThat(response.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::yhteystiedot)
                .all {
                    extracting { it.rooli to it.yhteyshenkilot.single().tilaaja }
                        .containsExactlyInAnyOrder(
                            HAKIJA to true,
                            TYON_SUORITTAJA to false,
                            RAKENNUTTAJA to false,
                            ASIANHOITAJA to false,
                        )
                    each { it.prop(Hakemusyhteystieto::tyyppi).isEqualTo(CustomerType.COMPANY) }
                }
        }
    }

    @Nested
    inner class HakemusResponse {
        @Test
        fun `when application does not exist should throw`() {
            assertThat(applicationRepository.findAll()).isEmpty()

            val exception = assertFailure { hakemusService.hakemusResponse(1234) }

            exception.hasClass(ApplicationNotFoundException::class)
        }

        @Nested
        inner class WithJohtoselvityshakemus {
            @Test
            fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
                val hanke = hankeFactory.saveMinimal(generated = true)
                val application =
                    hakemusFactory
                        .builder(USERNAME, hanke)
                        .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET, tilaaja = true)
                        .rakennuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                        .asianhoitaja()
                        .tyonSuorittaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                        .save()

                val response = hakemusService.hakemusResponse(application.id)

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
        }

        @Nested
        inner class WithKaivuilmoitus {
            @Test
            fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
                val hanke = hankeFactory.saveMinimal(generated = true)
                val application =
                    hakemusFactory
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET, tilaaja = true)
                        .rakennuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                        .asianhoitaja()
                        .tyonSuorittaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                        .save()

                val response = hakemusService.hakemusResponse(application.id)

                assertThat(response.applicationData as KaivuilmoitusDataResponse)
                    .hasAllCustomersWithContacts()
            }

            private fun Assert<KaivuilmoitusDataResponse>.hasAllCustomersWithContacts() {
                prop(KaivuilmoitusDataResponse::customerWithContacts)
                    .isNotNull()
                    .isCompanyCustomerWithOneContact(true)
                prop(KaivuilmoitusDataResponse::contractorWithContacts)
                    .isNotNull()
                    .isCompanyCustomerWithOneContact(false)
                prop(KaivuilmoitusDataResponse::propertyDeveloperWithContacts)
                    .isNotNull()
                    .isCompanyCustomerWithOneContact(false)
                prop(KaivuilmoitusDataResponse::representativeWithContacts)
                    .isNotNull()
                    .isCompanyCustomerWithOneContact(false)
            }
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

        @Nested
        inner class WithJohtoselvityshakemus {
            @Test
            fun `returns applications`() {
                val (_, hanke) = hankeFactory.builder(USERNAME).saveAsGenerated()

                val result = hakemusService.hankkeenHakemuksetResponse(hanke.hankeTunnus)

                val expectedHakemus =
                    HankkeenHakemusResponse(applicationRepository.findAll().single())
                assertThat(result.applications).hasSameElementsAs(listOf(expectedHakemus))
            }
        }

        @Nested
        inner class WithKaivuilmoitus {
            @Test
            fun `returns applications`() {
                val hanke = hankeFactory.saveMinimal(generated = true)
                hakemusFactory
                    .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .save()
                    .toResponse()

                val result = hakemusService.hankkeenHakemuksetResponse(hanke.hankeTunnus)

                val expectedHakemus =
                    HankkeenHakemusResponse(applicationRepository.findAll().single())
                assertThat(result.applications).hasSameElementsAs(listOf(expectedHakemus))
            }
        }
    }

    @Nested
    inner class Create {
        abstract inner class CreateTest {
            abstract val request: CreateHakemusRequest

            abstract fun Assert<Hakemus>.matchesRequest(hankeTunnus: String)

            private fun CreateHakemusRequest.withHanketunnus(hanketunnus: String) =
                when (this) {
                    is CreateJohtoselvityshakemusRequest -> copy(hankeTunnus = hanketunnus)
                    is CreateKaivuilmoitusRequest -> copy(hankeTunnus = hanketunnus)
                }

            @Test
            fun `throws exception when hanke does not exist`() {
                val failure = assertFailure { hakemusService.create(request, USERNAME) }

                failure.all {
                    hasClass(HankeNotFoundException::class)
                    messageContains("hankeTunnus ${request.hankeTunnus}")
                }
            }

            @Test
            fun `creates the hakemus`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)

                val result = hakemusService.create(request, USERNAME)

                val hakemus = hakemusService.getById(result.id)
                assertThat(hakemus).matchesRequest(hanke.hankeTunnus)
            }

            @Test
            fun `returns the created hakemus`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)

                val result = hakemusService.create(request, USERNAME)

                assertThat(result).matchesRequest(hanke.hankeTunnus)
            }

            @Test
            fun `writes the created hakemus to the audit log`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)
                auditLogRepository.deleteAll()

                val result = hakemusService.create(request, USERNAME)

                assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.CREATE) {
                    hasUserActor(USERNAME)
                    withTarget {
                        hasId(result.id)
                        prop(AuditLogTarget::type).isEqualTo(ObjectType.HAKEMUS)
                        prop(AuditLogTarget::objectBefore).isNull()
                        hasObjectAfter(result)
                    }
                }
            }
        }

        @Nested
        inner class WithJohtoselvityshakemus : CreateTest() {
            override val request: CreateJohtoselvityshakemusRequest =
                CreateHakemusRequestFactory.johtoselvitysRequest()

            override fun Assert<Hakemus>.matchesRequest(hankeTunnus: String) = all {
                prop(Hakemus::id).isGreaterThan(0)
                prop(Hakemus::hankeTunnus).isEqualTo(hankeTunnus)
                prop(Hakemus::alluid).isNull()
                prop(Hakemus::alluStatus).isNull()
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(Hakemus::applicationData)
                    .isInstanceOf(JohtoselvityshakemusData::class)
                    .matchesRequest()
            }

            private fun Assert<JohtoselvityshakemusData>.matchesRequest() = all {
                prop(JohtoselvityshakemusData::name).isEqualTo(request.name)
                prop(JohtoselvityshakemusData::postalAddress)
                    .hasStreetName(request.postalAddress!!.streetAddress.streetName!!)
                prop(JohtoselvityshakemusData::constructionWork).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::maintenanceWork).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::propertyConnectivity).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::emergencyWork).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::rockExcavation).isNotNull().isFalse()
                prop(JohtoselvityshakemusData::workDescription).isEqualTo(request.workDescription)
                prop(JohtoselvityshakemusData::startTime).isNull()
                prop(JohtoselvityshakemusData::endTime).isNull()
                prop(JohtoselvityshakemusData::pendingOnClient).isTrue()
                prop(JohtoselvityshakemusData::areas).isNull()
                prop(JohtoselvityshakemusData::yhteystiedot).isEmpty()
            }
        }

        @Nested
        inner class WithKaivuilmoitus : CreateTest() {
            override val request: CreateKaivuilmoitusRequest =
                CreateHakemusRequestFactory.kaivuilmoitusRequest()

            override fun Assert<Hakemus>.matchesRequest(hankeTunnus: String) = all {
                prop(Hakemus::id).isGreaterThan(0)
                prop(Hakemus::hankeTunnus).isEqualTo(hankeTunnus)
                prop(Hakemus::alluid).isNull()
                prop(Hakemus::alluStatus).isNull()
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::applicationType).isEqualTo(ApplicationType.EXCAVATION_NOTIFICATION)
                prop(Hakemus::applicationData)
                    .isInstanceOf(KaivuilmoitusData::class)
                    .matchesRequest()
            }

            private fun Assert<KaivuilmoitusData>.matchesRequest() = all {
                prop(KaivuilmoitusData::pendingOnClient).isTrue()
                prop(KaivuilmoitusData::name).isEqualTo(request.name)
                prop(KaivuilmoitusData::workDescription).isEqualTo(request.workDescription)
                prop(KaivuilmoitusData::constructionWork).isEqualTo(request.constructionWork)
                prop(KaivuilmoitusData::maintenanceWork).isEqualTo(request.maintenanceWork)
                prop(KaivuilmoitusData::emergencyWork).isEqualTo(request.emergencyWork)
                prop(KaivuilmoitusData::cableReportDone).isEqualTo(request.cableReportDone)
                prop(KaivuilmoitusData::rockExcavation).isEqualTo(request.rockExcavation)
                prop(KaivuilmoitusData::cableReports).isEqualTo(request.cableReports)
                prop(KaivuilmoitusData::placementContracts).isEqualTo(request.placementContracts)
                prop(KaivuilmoitusData::requiredCompetence).isEqualTo(request.requiredCompetence)
                prop(KaivuilmoitusData::startTime).isNull()
                prop(KaivuilmoitusData::endTime).isNull()
                prop(KaivuilmoitusData::areas).isNull()
                prop(KaivuilmoitusData::yhteystiedot).isEmpty()
                prop(KaivuilmoitusData::invoicingCustomer).isNull()
                prop(KaivuilmoitusData::additionalInfo).isNull()
            }
        }
    }

    @Nested
    inner class CreateJohtoselvitys {
        private val hakemusNimi = "Johtoselvitys for a private property"

        @Test
        fun `saves the new hakemus`() {
            val hanke = hankeFactory.saveMinimal(nimi = hakemusNimi)

            hakemusService.createJohtoselvitys(hanke, USERNAME)

            assertThat(applicationRepository.findAll()).single().all {
                prop(ApplicationEntity::id).isNotNull()
                prop(ApplicationEntity::alluid).isNull()
                prop(ApplicationEntity::alluStatus).isNull()
                prop(ApplicationEntity::applicationIdentifier).isNull()
                prop(ApplicationEntity::userId).isEqualTo(USERNAME)
                prop(ApplicationEntity::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(ApplicationEntity::applicationData)
                    .isInstanceOf(CableReportApplicationData::class)
                    .all {
                        prop(ApplicationData::name).isEqualTo(hakemusNimi)
                        prop(ApplicationData::applicationType)
                            .isEqualTo(ApplicationType.CABLE_REPORT)
                        prop(ApplicationData::pendingOnClient).isTrue()
                        prop(ApplicationData::areas).isNull()
                        prop(ApplicationData::customersWithContacts).isEmpty()
                        prop(CableReportApplicationData::startTime).isNull()
                        prop(CableReportApplicationData::endTime).isNull()
                    }
            }
        }

        @Test
        fun `returns the created hakemus`() {
            val hanke = hankeFactory.saveMinimal(nimi = hakemusNimi)

            val hakemus = hakemusService.createJohtoselvitys(hanke, USERNAME)

            assertThat(hakemus).all {
                prop(Hakemus::id).isNotNull()
                prop(Hakemus::alluid).isNull()
                prop(Hakemus::alluStatus).isNull()
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(Hakemus::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(Hakemus::applicationData).isInstanceOf(JohtoselvityshakemusData::class).all {
                    prop(HakemusData::name).isEqualTo(hakemusNimi)
                    prop(HakemusData::pendingOnClient).isTrue()
                    prop(JohtoselvityshakemusData::postalAddress).isNull()
                    prop(HakemusData::startTime).isNull()
                    prop(HakemusData::endTime).isNull()
                    prop(HakemusData::areas).isNull()
                    prop(HakemusData::yhteystiedot).isEmpty()
                }
            }
        }

        @Test
        fun `writes to audit logs`() {
            val hanke = hankeFactory.saveMinimal(nimi = hakemusNimi)
            auditLogRepository.deleteAll()

            val hakemus = hakemusService.createJohtoselvitys(hanke, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                withTarget {
                    prop(AuditLogTarget::objectBefore).isNull()
                    hasObjectAfter<Hakemus> { prop(Hakemus::id).isEqualTo(hakemus.id) }
                }
            }
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

        @Nested
        inner class WithJohtoselvitys {
            @Test
            fun `throws exception when the application does not exist`() {
                assertThat(applicationRepository.findAll()).isEmpty()
                val request =
                    HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(1234, request, USERNAME)
                }

                exception.hasClass(ApplicationNotFoundException::class)
            }

            @Test
            fun `throws exception when the application has been sent to Allu`() {
                val entity = hakemusFactory.builder().withStatus(alluId = 21).saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(ApplicationAlreadySentException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("alluId=21")
                }
            }

            @Test
            fun `does not create a new audit log entry when the application has not changed`() {
                val entity = hakemusFactory.builder().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val originalAuditLogSize =
                    auditLogRepository.findByType(ObjectType.APPLICATION).size
                // The saved hakemus has null in areas, but the response replaces it with an empty
                // list,
                // so set the value back to null in the request.
                val request = hakemus.toUpdateRequest().withAreas(null)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
                assertThat(applicationLogs).hasSize(originalAuditLogSize)
            }

            @Test
            fun `throws exception when there are invalid geometry in areas`() {
                val entity = hakemusFactory.builder().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest().withArea(intersectingArea)

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
                val entity = hakemusFactory.builder().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val requestYhteystietoId = UUID.randomUUID()
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, requestYhteystietoId)

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(InvalidHakemusyhteystietoException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("role=$HAKIJA")
                    messageContains("yhteystietoId=null")
                    messageContains("newId=$requestYhteystietoId")
                }
            }

            @Test
            fun `throws exception when the request has different persisted contact than the application`() {
                val entity = hakemusFactory.builder().hakija().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val originalYhteystietoId = hakemusyhteystietoRepository.findAll().first().id
                val requestYhteystietoId = UUID.randomUUID()
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, requestYhteystietoId)

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(InvalidHakemusyhteystietoException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("role=$HAKIJA")
                    messageContains("yhteystietoId=$originalYhteystietoId")
                    messageContains("newId=$requestYhteystietoId")
                }
            }

            @Test
            fun `throws exception when the request has a contact that is not a user on hanke`() {
                val entity = hakemusFactory.builder().hakija().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val requestHankekayttajaId = UUID.randomUUID()
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, yhteystieto.id, requestHankekayttajaId)

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
                val hanke = hankeFactory.builder().withHankealue().saveEntity()
                val entity = hakemusFactory.builder(hanke).saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest().withArea(notInHankeArea)

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(ApplicationGeometryNotInsideHankeException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains(hanke.logString())
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
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val kayttaja = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(
                            CustomerType.COMPANY,
                            yhteystieto.id,
                            kayttaja.id,
                            newKayttaja.id
                        )
                        .withWorkDescription("New work description")

                val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .all {
                        prop(JohtoselvitysHakemusDataResponse::workDescription)
                            .isEqualTo("New work description")
                        prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                            .isNotNull()
                            .prop(CustomerWithContactsResponse::contacts)
                            .extracting { it.hankekayttajaId }
                            .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                    }
                val applicationLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
                assertThat(applicationLogs).hasSize(originalAuditLogSize + 1)

                val persistedHakemus = hakemusService.hakemusResponse(updatedHakemus.id)
                assertThat(persistedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .all {
                        prop(JohtoselvitysHakemusDataResponse::workDescription)
                            .isEqualTo("New work description")
                        prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                            .isNotNull()
                            .prop(CustomerWithContactsResponse::contacts)
                            .extracting { it.hankekayttajaId }
                            .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                    }
            }

            @Test
            fun `removes existing yhteyshenkilot from an yhteystieto`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val kayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
                val kayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke)
                        .hakija()
                        .tyonSuorittaja(kayttaja1, kayttaja2)
                        .save()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val tyonSuorittaja =
                    hakemusyhteystietoRepository.findAll().single { it.rooli == TYON_SUORITTAJA }
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withContractor(
                            CustomerType.COMPANY,
                            tyonSuorittaja.id,
                            hankekayttajaIds = arrayOf()
                        )

                val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::contractorWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .isEmpty()

                val persistedHakemus = hakemusService.hakemusResponse(updatedHakemus.id)
                assertThat(persistedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::contractorWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .isEmpty()
            }

            @Test
            fun `adds a new yhteystieto and an yhteyshenkilo for it at the same time`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke)
                        .withWorkDescription("Old work description")
                        .save()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                assertThat(hakemus.applicationData.customerWithContacts).isNull()
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(
                            CustomerType.COMPANY,
                            yhteystietoId = null,
                            hankekayttajaIds = arrayOf(newKayttaja.id)
                        )

                val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .extracting { it.hankekayttajaId }
                    .containsExactly(newKayttaja.id)

                val persistedHakemus = hakemusService.hakemusResponse(updatedHakemus.id)
                assertThat(persistedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .extracting { it.hankekayttajaId }
                    .containsExactly(newKayttaja.id)
            }

            @Test
            fun `sends email for new contacts`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val entity = hakemusFactory.builder(USERNAME, hanke).hakija().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, yhteystieto.id, newKayttaja.id)
                        .withContractor(CustomerType.COMPANY, null, newKayttaja.id)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients.single().toString())
                    .isEqualTo(newKayttaja.sahkoposti)
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Sinut on lisätty hakemukselle / Du har lagts till i en ansökan / You have been added to an application"
                    )
                assertThat(email.textBody())
                    .contains(
                        "laatimassa johtoselvityshakemusta hankkeelle \"${hanke.nimi}\" (${hanke.hankeTunnus})"
                    )
            }

            @Test
            fun `updates project name when application name is changed`() {
                val entity = hakemusFactory.builderWithGeneratedHanke().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest().withName("New name")

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(hankeRepository.findAll().single()).all {
                    prop(HankeEntity::nimi).isEqualTo("New name")
                }
            }
        }

        @Nested
        inner class WithKaivuilmoitus {

            @Test
            fun `throws exception when the application does not exist`() {
                assertThat(applicationRepository.findAll()).isEmpty()
                val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(1234, request, USERNAME)
                }

                exception.hasClass(ApplicationNotFoundException::class)
            }

            @Test
            fun `throws exception when the application has been sent to Allu`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .withStatus(alluId = 21)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(ApplicationAlreadySentException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("alluId=21")
                }
            }

            @Test
            fun `does not create a new audit log entry when the application has not changed`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val originalAuditLogSize =
                    auditLogRepository.findByType(ObjectType.APPLICATION).size
                // The saved hakemus has null in areas, but the response replaces it with an empty
                // list,
                // so set the value back to null in the request.
                val request = hakemus.toUpdateRequest().withAreas(null)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                val applicationLogs = auditLogRepository.findByType(ObjectType.APPLICATION)
                assertThat(applicationLogs).hasSize(originalAuditLogSize)
            }

            @Test
            fun `throws exception when there are invalid geometry in areas`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
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
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
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
                    messageContains("role=${HAKIJA}")
                    messageContains("yhteystietoId=null")
                    messageContains("newId=$requestYhteystietoId")
                }
            }

            @Test
            fun `throws exception when the request has different persisted contact than the application`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
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
                    messageContains("role=${HAKIJA}")
                    messageContains("yhteystietoId=$originalYhteystietoId")
                    messageContains("newId=$requestYhteystietoId")
                }
            }

            @Test
            fun `throws exception when the request has a contact that is not a user on hanke`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
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
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest().withAreas(listOf(notInHankeArea))

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(ApplicationGeometryNotInsideHankeException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains(hanke.logString())
                    messageContains("geometry=${notInHankeArea.geometry.toJsonString()}")
                }
            }

            @Test
            fun `saves updated data and creates an audit log`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .withWorkDescription("Old work description")
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val kayttaja = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
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

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(KaivuilmoitusDataResponse::class)
                    .all {
                        prop(KaivuilmoitusDataResponse::workDescription)
                            .isEqualTo("New work description")
                        prop(KaivuilmoitusDataResponse::customerWithContacts)
                            .isNotNull()
                            .prop(CustomerWithContactsResponse::contacts)
                            .extracting { it.hankekayttajaId }
                            .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                    }

                val applicationLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
                assertThat(applicationLogs).hasSize(originalAuditLogSize + 1)

                val persistedHakemus = hakemusService.hakemusResponse(updatedHakemus.id)
                assertThat(persistedHakemus.applicationData)
                    .isInstanceOf(KaivuilmoitusDataResponse::class)
                    .all {
                        prop(KaivuilmoitusDataResponse::workDescription)
                            .isEqualTo("New work description")
                        prop(KaivuilmoitusDataResponse::customerWithContacts)
                            .isNotNull()
                            .prop(CustomerWithContactsResponse::contacts)
                            .extracting { it.hankekayttajaId }
                            .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                    }
            }

            @Test
            fun `removes existing yhteyshenkilot from an yhteystieto`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val kayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
                val kayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke)
                        .hakija()
                        .tyonSuorittaja(kayttaja1, kayttaja2)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val tyonSuorittaja =
                    hakemusyhteystietoRepository.findAll().single { it.rooli == TYON_SUORITTAJA }
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withContractor(
                            CustomerType.COMPANY,
                            tyonSuorittaja.id,
                            hankekayttajaIds = arrayOf()
                        )

                val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::contractorWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .isEmpty()
            }

            @Test
            fun `adds a new yhteystieto and an yhteyshenkilo for it at the same time`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke)
                        .withWorkDescription("Old work description")
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                assertThat(hakemus.applicationData.customerWithContacts).isNull()
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(
                            CustomerType.COMPANY,
                            yhteystietoId = null,
                            hankekayttajaIds = arrayOf(newKayttaja.id)
                        )

                val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .extracting { it.hankekayttajaId }
                    .containsExactly(newKayttaja.id)

                val persistedHakemus = hakemusService.hakemusResponse(updatedHakemus.id)
                assertThat(persistedHakemus.applicationData)
                    .isInstanceOf(JohtoselvitysHakemusDataResponse::class)
                    .prop(JohtoselvitysHakemusDataResponse::customerWithContacts)
                    .isNotNull()
                    .prop(CustomerWithContactsResponse::contacts)
                    .extracting { it.hankekayttajaId }
                    .containsExactly(newKayttaja.id)
            }

            @Test
            fun `sends email for new contacts`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, yhteystieto.id, newKayttaja.id)
                        .withContractor(CustomerType.COMPANY, null, newKayttaja.id)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                val email = greenMail.firstReceivedMessage()
                assertThat(email.allRecipients.single().toString())
                    .isEqualTo(newKayttaja.sahkoposti)
                assertThat(email.subject)
                    .isEqualTo(
                        "Haitaton: Sinut on lisätty hakemukselle / Du har lagts till i en ansökan / You have been added to an application"
                    )
                assertThat(email.textBody())
                    .contains(
                        "laatimassa kaivuilmoitusta hankkeelle \"${hanke.nimi}\" (${hanke.hankeTunnus})"
                    )
            }

            @Test
            fun `updates project name when application name is changed`() {
                val entity =
                    hakemusFactory
                        .builderWithGeneratedHanke(tyyppi = ApplicationType.EXCAVATION_NOTIFICATION)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest().withName("New name")

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(hankeRepository.findAll().single()).all {
                    prop(HankeEntity::nimi).isEqualTo("New name")
                }
            }
        }
    }

    @Nested
    inner class SendHakemus {
        private val alluId = 35124

        private val areaOutsideDefaultHanke: ApplicationArea =
            ApplicationFactory.createApplicationArea(geometry = GeometriaFactory.thirdPolygon)

        @Test
        fun `throws exception when the application doesn't exist`() {
            val failure = assertFailure { hakemusService.sendHakemus(1234, USERNAME) }

            failure.all {
                hasClass(ApplicationNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `throws exception when the application has been sent before`() {
            val application =
                hakemusFactory.builder().withMandatoryFields().withStatus(alluId = alluId).save()

            val failure = assertFailure { hakemusService.sendHakemus(application.id, USERNAME) }

            failure.all {
                hasClass(ApplicationAlreadySentException::class)
                messageContains("id=${application.id}")
                messageContains("alluId=$alluId")
                messageContains("status=PENDING")
            }
        }

        @Test
        fun `throws exception when the application fails validation`() {
            val application =
                hakemusFactory.builder().withMandatoryFields().withRockExcavation(null).save()

            val failure = assertFailure { hakemusService.sendHakemus(application.id, USERNAME) }

            failure
                .isInstanceOf(InvalidHakemusDataException::class)
                .messageContains("applicationData.rockExcavation")
        }

        @Test
        fun `skips the check for hakemusalueet inside hankealueet when the hanke is generated`() {
            val application =
                hakemusFactory.builderWithGeneratedHanke().withMandatoryFields().save()
            val areas = application.applicationData.areas!!
            val hanke = hankeRepository.findAll().single()
            hankeRepository.save(hanke.apply { alueet = mutableListOf() })
            assertThat(geometriatDao.isInsideHankeAlueet(hanke.id, areas[0].geometry)).isFalse()
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            hakemusService.sendHakemus(application.id, USERNAME)

            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, any())
                alluClient.getApplicationInformation(any())
            }
        }

        @Test
        fun `sets pendingOnClient to false`() {
            val hanke = hankeFactory.saveWithAlue()
            val hakemus =
                hakemusFactory
                    .builder(hankeEntity = hanke)
                    .withMandatoryFields()
                    .withPendingOnClient(true)
                    .save()
            val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val applicationData =
                (hakemus.applicationData as JohtoselvityshakemusData)
                    .setOrdererForContractor(founder.id)
                    .toAlluData(hanke.hankeTunnus)
                    .copy(pendingOnClient = false)
            every { alluClient.create(applicationData) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            val responseApplicationData = response.applicationData as JohtoselvityshakemusData
            assertThat(responseApplicationData.pendingOnClient).isFalse()
            val savedApplication = applicationRepository.findById(hakemus.id).get()
            val savedApplicationData =
                savedApplication.applicationData as CableReportApplicationData
            assertThat(savedApplicationData.pendingOnClient).isFalse()
            verifySequence {
                alluClient.create(applicationData)
                alluClient.addAttachment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws an exception when the application is already beyond pending in Allu`() {
            val hanke = hankeFactory.saveWithAlue()
            val application =
                hakemusFactory
                    .builder(hankeEntity = hanke)
                    .withMandatoryFields()
                    .inHandling(alluId = alluId)
                    .saveEntity()

            val failure = assertFailure { hakemusService.sendHakemus(application.id, USERNAME) }

            failure.all {
                hasClass(ApplicationAlreadySentException::class)
                messageContains("id=${application.id}")
                messageContains("alluId=$alluId")
                messageContains("status=HANDLING")
            }
        }

        @Test
        fun `sends application and saves alluid even when the status query fails`() {
            val hanke = hankeFactory.saveWithAlue()
            val application =
                hakemusFactory.builder(hankeEntity = hanke).withMandatoryFields().saveEntity()
            val applicationData = application.applicationData as CableReportApplicationData
            val expectedDataAfterSend = applicationData.copy(pendingOnClient = false)
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } throws AlluException()

            val response = hakemusService.sendHakemus(application.id, USERNAME)

            assertThat(response).all {
                prop(Hakemus::alluid).isEqualTo(alluId)
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::alluStatus).isNull()
            }
            assertThat(applicationRepository.getReferenceById(application.id)).all {
                prop(ApplicationEntity::alluid).isEqualTo(alluId)
                prop(ApplicationEntity::applicationData).isEqualTo(expectedDataAfterSend)
                prop(ApplicationEntity::applicationIdentifier).isNull()
                prop(ApplicationEntity::alluStatus).isNull()
            }

            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws an exception when application area is outside hankealue`() {
            val hanke = hankeFactory.saveWithAlue()
            val hakemus =
                hakemusFactory
                    .builder(hanke)
                    .withMandatoryFields()
                    .withArea(areaOutsideDefaultHanke)
                    .save()

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, USERNAME) }

            failure.all {
                hasClass(ApplicationGeometryNotInsideHankeException::class)
                messageContains(hakemus.logString())
                messageContains(hanke.logString())
                messageContains(
                    "hakemus geometry=${areaOutsideDefaultHanke.geometry.toJsonString()}"
                )
            }
        }

        @Test
        fun `cancels the sent application before throwing when uploading initial attachments fails`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val applicationEntity = applicationRepository.getReferenceById(hakemus.id)
            attachmentFactory.save(application = applicationEntity).withContent()
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.addAttachments(alluId, any(), any()) } throws AlluException()
            justRun { alluClient.cancel(alluId) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 4141

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, USERNAME) }

            failure.hasClass(AlluException::class)
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, any())
                alluClient.addAttachments(alluId, any(), any())
                alluClient.cancel(alluId)
                alluClient.sendSystemComment(alluId, ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG)
            }
        }

        @Test
        fun `saves the Allu ID and status even when sending the form data attachment fails`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            every { alluClient.create(any()) } returns alluId
            every { alluClient.addAttachment(alluId, any()) } throws AlluException()
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            assertThat(response.alluid).isEqualTo(alluId)
            assertThat(response.alluStatus).isEqualTo(ApplicationStatus.PENDING)
            val savedHakemus = applicationRepository.getReferenceById(hakemus.id)
            assertThat(savedHakemus.alluid).isEqualTo(alluId)
            assertThat(savedHakemus.alluStatus).isEqualTo(ApplicationStatus.PENDING)
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `creates a new application to Allu and saves the ID and status to database`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val applicationEntity = applicationRepository.getReferenceById(hakemus.id)
            attachmentFactory.save(application = applicationEntity).withContent()
            val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val hakemusData = hakemus.applicationData as JohtoselvityshakemusData
            val expectedDataAfterSend =
                hakemusData.copy(pendingOnClient = false).setOrdererForContractor(founder.id)
            val expectedAlluRequest = expectedDataAfterSend.toAlluData(hakemus.hankeTunnus)
            every { alluClient.create(expectedAlluRequest) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            justRun { alluClient.addAttachments(alluId, any(), any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            assertThat(response).all {
                prop(Hakemus::alluid).isEqualTo(alluId)
                prop(Hakemus::applicationIdentifier)
                    .isEqualTo(ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER)
                prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
            }
            assertThat(applicationRepository.getReferenceById(hakemus.id)).all {
                prop(ApplicationEntity::alluid).isEqualTo(alluId)
                prop(ApplicationEntity::applicationIdentifier)
                    .isEqualTo(ApplicationFactory.DEFAULT_APPLICATION_IDENTIFIER)
                prop(ApplicationEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
            }
            verifySequence {
                alluClient.create(expectedAlluRequest)
                alluClient.addAttachment(alluId, any())
                alluClient.addAttachments(alluId, any(), any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `sets the orderer on the correct contact`() {
            val hanke = hankeFactory.saveWithAlue()
            val founder = hankeKayttajaFactory.getFounderFromHanke(hanke)
            val otherKayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
            val otherKayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .hakija(otherKayttaja1, founder)
                    .tyonSuorittaja(founder, otherKayttaja2)
                    .rakennuttaja(otherKayttaja1, founder)
                    .asianhoitaja(founder, otherKayttaja2)
                    .save()
            val hakemusData = hakemus.applicationData as JohtoselvityshakemusData
            val expectedDataAfterSend =
                hakemusData.copy(pendingOnClient = false).setOrdererForCustomer(founder.id)
            val expectedAlluRequest = expectedDataAfterSend.toAlluData(hakemus.hankeTunnus)
            every { alluClient.create(expectedAlluRequest) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            assertThat(response).prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
            assertThat(hakemusService.getById(hakemus.id))
                .prop(Hakemus::applicationData)
                .prop(HakemusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { Pair(it.hankekayttajaId, it.tilaaja) }
                .contains(Pair(founder.id, true))
            verifySequence {
                alluClient.create(expectedAlluRequest)
                alluClient.addAttachment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws exception when sender is not a contact`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").withMandatoryFields().save()

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, USERNAME) }

            failure.all {
                hasClass(UserNotInContactsException::class)
                messageContains("id=${hakemus.id}")
                messageContains("alluId=null")
                messageContains("identifier=null")
            }
        }

        @Test
        fun `saves disclosure logs for the sent personal info`() {
            val hanke = hankeFactory.saveWithAlue()
            val founder = hankeKayttajaFactory.getFounderFromHanke(hanke)
            val hakijaYhteystieto =
                HakemusyhteystietoFactory.create(
                    tyyppi = CustomerType.PERSON,
                    nimi = "Ylevi Yhteyshenkilö",
                    sahkoposti = "ylevi@hakemus.info",
                    puhelinnumero = "111222333",
                    ytunnus = null,
                )
            val asianhoitajaYhteystieto =
                hakijaYhteystieto.copy(
                    nimi = "Tytti Työläinen",
                    sahkoposti = "tytti@hakeus.info",
                    puhelinnumero = "999888777"
                )
            val hakemus =
                hakemusFactory
                    .builder(userId = "Other user")
                    .withMandatoryFields()
                    .hakija(hakijaYhteystieto, founder)
                    .tyonSuorittaja(asianhoitajaYhteystieto, founder)
                    .asianhoitaja()
                    .save()
            auditLogRepository.deleteAll()
            every { alluClient.create(any()) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            hakemusService.sendHakemus(hakemus.id, USERNAME)

            val yhteystietoEntries = auditLogRepository.findAll()
            assertThat(yhteystietoEntries).hasSize(5)
            assertThat(auditLogRepository.findByType(ObjectType.ALLU_CUSTOMER)).hasSize(2)
            assertThat(auditLogRepository.findByType(ObjectType.ALLU_CONTACT)).hasSize(3)
            assertThat(yhteystietoEntries).each {
                it.isSuccess(Operation.READ) {
                    hasServiceActor("Allu")
                    withTarget {
                        hasId(hakemus.id)
                        prop(AuditLogTarget::objectBefore).isNotNull()
                        prop(AuditLogTarget::objectAfter).isNull()
                    }
                }
            }
            val expectedHakijaCustomer =
                Customer(
                    type = CustomerType.PERSON,
                    name = "Ylevi Yhteyshenkilö",
                    postalAddress = null,
                    email = "ylevi@hakemus.info",
                    phone = "111222333",
                    registryKey = null,
                    ovt = null,
                    invoicingOperator = null,
                    country = "FI",
                    sapCustomerNumber = null,
                )
            val expectedAsianhoitajaCustomer =
                expectedHakijaCustomer.copy(
                    name = "Tytti Työläinen",
                    email = "tytti@hakeus.info",
                    phone = "999888777",
                )
            val expectedPerustajaContact =
                Contact(
                    name = founder.fullName(),
                    email = founder.sahkoposti,
                    phone = founder.puhelin,
                    orderer = true,
                )
            val expectedAsianhoitajaContact =
                Contact(
                    name =
                        "${KAYTTAJA_INPUT_ASIANHOITAJA.etunimi} ${KAYTTAJA_INPUT_ASIANHOITAJA.sukunimi}",
                    email = KAYTTAJA_INPUT_ASIANHOITAJA.email,
                    phone = KAYTTAJA_INPUT_ASIANHOITAJA.puhelin,
                    orderer = false,
                )
            val expectedObjects =
                listOf(
                    AlluCustomerWithRole(HAKIJA, expectedHakijaCustomer),
                    AlluCustomerWithRole(TYON_SUORITTAJA, expectedAsianhoitajaCustomer),
                    AlluContactWithRole(HAKIJA, expectedPerustajaContact),
                    AlluContactWithRole(
                        TYON_SUORITTAJA,
                        expectedPerustajaContact.copy(orderer = false)
                    ),
                    AlluContactWithRole(ASIANHOITAJA, expectedAsianhoitajaContact),
                )
            assertThat(yhteystietoEntries.map { it.message.auditEvent.target.objectBefore })
                .hasSameElementsAs(expectedObjects.map { it.toJsonString() })
            verifySequence {
                alluClient.create(any())
                alluClient.addAttachment(alluId, any())
                alluClient.getApplicationInformation(alluId)
            }
        }
    }

    @Nested
    inner class DeleteWithOrphanGeneratedHankeRemoval {
        @Test
        fun `deletes hanke when deleted application is the only one on a generated hanke`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withNoAlluFields().save()
            auditLogRepository.deleteAll()

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = true))
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            val auditLogEntry = auditLogRepository.findByType(ObjectType.HANKE)
            assertThat(auditLogEntry).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME)
            }
        }

        @Test
        fun `deletes all attachments when deleting an application`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = hakemus).withContent()
            attachmentFactory.save(application = hakemus).withContent()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).hasSize(2)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id)
                    )
                )
                .hasSize(2)

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = true))
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id)
                    )
                )
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).isEmpty()
        }

        @Test
        fun `doesn't delete hanke when hanke is not generated`() {
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = false))
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)).isNotNull()
        }

        @Test
        fun `doesn't delete hanke when a generated hanke has other applications`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val hakemus1 = hakemusFactory.builder(hanke).withNoAlluFields().saveEntity()
            val hakemus2 = hakemusFactory.builder(hanke).withNoAlluFields().saveEntity()
            assertThat(applicationRepository.findAll()).hasSize(2)

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus1.id, USERNAME)

            assertThat(result).isEqualTo(ApplicationDeletionResultDto(hankeDeleted = false))
            assertThat(applicationRepository.findAll())
                .single()
                .prop(ApplicationEntity::id)
                .isEqualTo(hakemus2.id)
            assertThat(hankeRepository.findByHankeTunnus(hanke.hankeTunnus)).isNotNull()
        }

        @Test
        fun `deletes hankekayttajat when deleting the generated hanke`() {
            val application =
                hakemusFactory
                    .builderWithGeneratedHanke()
                    .withNoAlluFields()
                    .hakija()
                    .tyonSuorittaja()
                    .rakennuttaja()
                    .asianhoitaja()
                    .saveEntity()

            hakemusService.deleteWithOrphanGeneratedHankeRemoval(application.id, USERNAME)

            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
        }
    }

    @Nested
    inner class DownloadDecision {
        private val alluId = 134
        private val decisionPdf: ByteArray =
            "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()

        @Test
        fun `when unknown ID should throw`() {
            val failure = assertFailure { hakemusService.downloadDecision(1234, USERNAME) }

            failure.all {
                hasClass(ApplicationNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `when no alluid should throw`() {
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()

            val failure = assertFailure { hakemusService.downloadDecision(hakemus.id, USERNAME) }

            failure.all {
                hasClass(ApplicationDecisionNotFoundException::class)
                messageContains("id=${hakemus.id}")
            }
            verify { alluClient wasNot Called }
        }

        @Test
        fun `when no decision in Allu should throw`() {
            val hakemus = hakemusFactory.builder().inHandling(alluId = alluId).save()
            every { alluClient.getDecisionPdf(alluId) }
                .throws(ApplicationDecisionNotFoundException(""))

            val failure = assertFailure { hakemusService.downloadDecision(hakemus.id, USERNAME) }

            failure.hasClass(ApplicationDecisionNotFoundException::class)
            verify { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `when decision exists should return it`() {
            val hakemus = hakemusFactory.builder().inHandling(alluId = alluId).save()
            every { alluClient.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = hakemusService.downloadDecision(hakemus.id, USERNAME)

            assertThat(filename).isNotNull().isEqualTo(hakemus.applicationIdentifier)
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `when application has no identifier should use default file name`() {
            val hakemus =
                hakemusFactory.builder().withStatus(alluId = alluId, identifier = null).save()
            every { alluClient.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = hakemusService.downloadDecision(hakemus.id, USERNAME)

            assertThat(filename).isNotNull().isEqualTo("paatos")
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { alluClient.getDecisionPdf(alluId) }
        }
    }

    @Nested
    inner class CancelAndDelete {
        private val alluId = 73

        @Test
        fun `deletes application and all its attachments when application not in Allu`() {
            val application = hakemusFactory.builder().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = application).withContent()
            attachmentFactory.save(application = application).withContent()
            val hakemus = hakemusService.getById(application.id)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id)
                    )
                )
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .hasSize(2)

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id)
                    )
                )
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .isEmpty()
        }

        @Test
        fun `writes audit log for the deleted application`() {
            TestUtils.addMockedRequestIp()
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()
            auditLogRepository.deleteAll()

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(auditLogRepository.findAll()).single().isSuccess(Operation.DELETE) {
                hasUserActor(USERNAME, TestUtils.mockedIp)
                withTarget {
                    prop(AuditLogTarget::id).isEqualTo(hakemus.id.toString())
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HAKEMUS)
                    hasObjectBefore(hakemus)
                    prop(AuditLogTarget::objectAfter).isNull()
                }
            }
        }

        @Test
        fun `cancels the application in Allu before deleting when the application is pending in Allu`() {
            val hakemus =
                hakemusFactory.builder().withStatus(ApplicationStatus.PENDING, alluId).save()
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId, ApplicationStatus.PENDING)
            justRun { alluClient.cancel(alluId) }
            every { alluClient.sendSystemComment(alluId, any()) } returns 1324

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(applicationRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationInformation(alluId)
                alluClient.cancel(alluId)
                alluClient.sendSystemComment(alluId, ALLU_USER_CANCELLATION_MSG)
            }
        }

        @Test
        fun `throws an exception when the application is past pending in Allu`() {
            val hakemus =
                hakemusFactory.builder().withStatus(ApplicationStatus.PENDING, alluId).save()
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId, ApplicationStatus.APPROVED)

            val failure = assertFailure { hakemusService.cancelAndDelete(hakemus, USERNAME) }

            failure.all {
                hasClass(ApplicationAlreadyProcessingException::class)
                messageContains("id=${hakemus.id}")
                messageContains("alluId=$alluId")
            }
            assertThat(applicationRepository.findAll()).hasSize(1)
            verifySequence { alluClient.getApplicationInformation(alluId) }
        }

        @Test
        fun `deletes yhteystiedot and yhteyshenkilot but no hankekayttaja`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withNoAlluFields()
                    .hakija()
                    .tyonSuorittaja()
                    .rakennuttaja()
                    .asianhoitaja()
                    .save()

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role
        }
    }

    @Nested
    inner class IsStillPending {
        private val alluId = 123

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class)
        @NullSource
        fun `returns true when alluId is null`(status: ApplicationStatus?) {
            assertThat(hakemusService.isStillPending(null, status)).isTrue()

            verify { alluClient wasNot Called }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        @NullSource
        fun `returns true when status is pending and allu confirms it`(status: ApplicationStatus?) {
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(
                    status = status ?: ApplicationStatus.PENDING
                )

            assertThat(hakemusService.isStillPending(alluId, status)).isTrue()

            verify { alluClient.getApplicationInformation(alluId) }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(value = ApplicationStatus::class, names = ["PENDING", "PENDING_CLIENT"])
        @NullSource
        fun `returns false when status is pending but status in Allu is handling`(
            status: ApplicationStatus?
        ) {
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

            assertThat(hakemusService.isStillPending(alluId, status)).isFalse()

            verify { alluClient.getApplicationInformation(alluId) }
        }

        @ParameterizedTest(name = "{displayName} ({arguments})")
        @EnumSource(
            value = ApplicationStatus::class,
            mode = EnumSource.Mode.EXCLUDE,
            names = ["PENDING", "PENDING_CLIENT"]
        )
        fun `returns false when status is not pending`(status: ApplicationStatus) {
            assertThat(hakemusService.isStillPending(alluId, status)).isFalse()

            verify { alluClient wasNot Called }
        }
    }

    @Nested
    inner class HandleApplicationUpdates {

        /** The timestamp used in the initial DB migration. */
        private val placeholderUpdateTime = OffsetDateTime.parse("2017-01-01T00:00:00Z")
        private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
        private val alluId = 42
        private val identifier = ApplicationHistoryFactory.defaultApplicationIdentifier

        @Test
        fun `updates the last updated time with empty histories`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)

            hakemusService.handleHakemusUpdates(listOf(), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
        }

        @Test
        fun `updates the hakemus statuses in the correct order`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId).save()
            val firstEventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z")
            val history =
                ApplicationHistoryFactory.create(
                    alluId,
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime.plusDays(5),
                        ApplicationStatus.PENDING
                    ),
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime.plusDays(10),
                        ApplicationStatus.HANDLING
                    ),
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime,
                        ApplicationStatus.PENDING
                    ),
                )

            hakemusService.handleHakemusUpdates(listOf(history), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
            val application = applicationRepository.getOneByAlluid(alluId)
            assertThat(application)
                .isNotNull()
                .prop("alluStatus", ApplicationEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(application!!.applicationIdentifier).isEqualTo(identifier)
        }

        @Test
        fun `ignores missing hakemus`() {
            assertThat(applicationRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId).save()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId + 2).save()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(alluId, "JS2300082"),
                    ApplicationHistoryFactory.create(alluId + 1, "JS2300083"),
                    ApplicationHistoryFactory.create(alluId + 2, "JS2300084"),
                )

            hakemusService.handleHakemusUpdates(histories, updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
            val applications = applicationRepository.findAll()
            assertThat(applications).hasSize(2)
            assertThat(applications.map { it.alluid }).containsExactlyInAnyOrder(alluId, alluId + 2)
            assertThat(applications.map { it.alluStatus })
                .containsExactlyInAnyOrder(
                    ApplicationStatus.PENDING_CLIENT,
                    ApplicationStatus.PENDING_CLIENT
                )
            assertThat(applications.map { it.applicationIdentifier })
                .containsExactlyInAnyOrder("JS2300082", "JS2300084")
        }

        @Test
        fun `sends email to the contacts when hakemus gets a decision`() {
            val hanke = hankeFactory.saveMinimal()
            val hakija = hankeKayttajaFactory.saveUser(hankeId = hanke.id)
            hakemusFactory
                .builder(USERNAME, hanke)
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .hakija(hakija)
                .saveEntity()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluId,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = ApplicationStatus.DECISION
                        )
                    ),
                )

            hakemusService.handleHakemusUpdates(histories, updateTime)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys $identifier / Ledningsutredning $identifier / Cable report $identifier"
                )
        }
    }
}

private fun JohtoselvityshakemusData.setOrdererForCustomer(
    kayttajaId: UUID
): JohtoselvityshakemusData =
    this.copy(customerWithContacts = customerWithContacts!!.setOrderer(kayttajaId))

private fun JohtoselvityshakemusData.setOrdererForContractor(
    kayttajaId: UUID
): JohtoselvityshakemusData =
    this.copy(contractorWithContacts = contractorWithContacts!!.setOrderer(kayttajaId))

private fun Hakemusyhteystieto.setOrderer(kayttajaId: UUID): Hakemusyhteystieto {
    val yhteyshenkilot =
        yhteyshenkilot.map { if (it.hankekayttajaId == kayttajaId) it.copy(tilaaja = true) else it }

    return copy(yhteyshenkilot = yhteyshenkilot)
}
