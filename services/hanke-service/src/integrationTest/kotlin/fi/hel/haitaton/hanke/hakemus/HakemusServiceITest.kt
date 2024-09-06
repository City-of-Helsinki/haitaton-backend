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
import assertk.assertions.key
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import assertk.assertions.startsWith
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluExcavationNotificationData
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.asUtc
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentContentService
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TestFile
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createExcavationNotificationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createTyoalue
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
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withRequiredCompetence
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.ASIANHOITAJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.RAKENNUTTAJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluCableReportData
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluExcavationNotificationData
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AlluContactWithRole
import fi.hel.haitaton.hanke.logging.AlluCustomerWithRole
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.paatos.PaatosTila
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.test.AlluException
import fi.hel.haitaton.hanke.test.Asserts.hasStreetName
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasServiceActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasTargetType
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.valmistumisilmoitus.Valmistumisilmoitus
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.time.LocalDate
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
    @Autowired private val hakemusRepository: HakemusRepository,
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
    @Autowired private val paatosFactory: PaatosFactory,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val alluStatusRepository: AlluStatusRepository,
    @Autowired private val hankeService: HankeService,
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
                hasClass(HakemusNotFoundException::class)
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
    inner class GetWithPaatokset {
        @Test
        fun `throws an exception when the hakemus does not exist`() {
            val failure = assertFailure { hakemusService.getWithPaatokset(1234) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `returns hakemus`() {
            val hakemus = hakemusFactory.builder(USERNAME).withMandatoryFields().save()

            val response = hakemusService.getWithPaatokset(hakemus.id)

            assertThat(response.hakemus).all {
                prop(Hakemus::id).isEqualTo(hakemus.id)
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::hankeId).isEqualTo(hakemus.hankeId)
                prop(Hakemus::id).isEqualTo(hakemus.id)
                prop(Hakemus::applicationData).isInstanceOf(JohtoselvityshakemusData::class).all {
                    prop(JohtoselvityshakemusData::name).isEqualTo(hakemus.applicationData.name)
                }
            }
            assertThat(response.paatokset).isEmpty()
        }

        @Test
        fun `returns paatokset when they exist`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.DECISION)
                    .save()
            val paatos1 = paatosFactory.save(hakemus, tila = PaatosTila.KORVATTU)
            val paatos2 = paatosFactory.save(hakemus, hakemus.applicationIdentifier + "-1")

            val response = hakemusService.getWithPaatokset(hakemus.id)

            assertThat(response.paatokset).containsExactlyInAnyOrder(paatos1, paatos2)
        }
    }

    @Nested
    inner class HakemusResponse {
        @Test
        fun `when application does not exist should throw`() {
            assertThat(hakemusRepository.findAll()).isEmpty()

            val exception = assertFailure { hakemusService.hakemusResponse(1234) }

            exception.hasClass(HakemusNotFoundException::class)
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
                val hakemus = hakemusFactory.builderWithGeneratedHanke().save()

                val result = hakemusService.hankkeenHakemuksetResponse(hakemus.hankeTunnus)

                val expectedHakemus = HankkeenHakemusResponse(hakemusRepository.findAll().single())
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

                val expectedHakemus = HankkeenHakemusResponse(hakemusRepository.findAll().single())
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

                assertThat(auditLogRepository.findByType(ObjectType.HAKEMUS)).single().isSuccess(
                    Operation.CREATE) {
                        hasUserActor(USERNAME)
                        withTarget {
                            hasId(result.id)
                            prop(AuditLogTarget::type).isEqualTo(ObjectType.HAKEMUS)
                            hasNoObjectBefore()
                            hasObjectAfter(result)
                        }
                    }
            }

            @Test
            fun `updates the hanke phase to RAKENTAMINEN`() {
                val hanke = hankeFactory.saveMinimal()
                val request = request.withHanketunnus(hanke.hankeTunnus)

                hakemusService.create(request, USERNAME)

                val updatedHanke = hankeRepository.findByHankeTunnus(hanke.hankeTunnus)!!
                assertThat(updatedHanke.vaihe).isEqualTo(Hankevaihe.RAKENTAMINEN)
            }

            @Test
            fun `writes the updated hanke to the audit log if the phase has changed`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
                val request = request.withHanketunnus(hanke.hankeTunnus)
                auditLogRepository.deleteAll()

                hakemusService.create(request, USERNAME)

                assertThat(auditLogRepository.findByType(ObjectType.HANKE)).single().isSuccess(
                    Operation.UPDATE) {
                        hasUserActor(USERNAME)
                        withTarget {
                            hasId(hanke.id)
                            prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE)
                            prop(AuditLogTarget::objectBefore)
                                .isNotNull()
                                .contains("\"vaihe\":\"OHJELMOINTI\"")
                            prop(AuditLogTarget::objectAfter)
                                .isNotNull()
                                .contains("\"vaihe\":\"RAKENTAMINEN\"")
                        }
                    }
            }

            @Test
            fun `does not write hanke to the audit log if the phase has not changed`() {
                val hanke =
                    hankeFactory
                        .builder(USERNAME)
                        .withHankealue()
                        .withVaihe(Hankevaihe.RAKENTAMINEN)
                        .save()
                val request = request.withHanketunnus(hanke.hankeTunnus)
                auditLogRepository.deleteAll()

                hakemusService.create(request, USERNAME)

                assertThat(auditLogRepository.findByType(ObjectType.HANKE)).isEmpty()
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

            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::id).isNotNull()
                prop(HakemusEntity::alluid).isNull()
                prop(HakemusEntity::alluStatus).isNull()
                prop(HakemusEntity::applicationIdentifier).isNull()
                prop(HakemusEntity::userId).isEqualTo(USERNAME)
                prop(HakemusEntity::applicationType).isEqualTo(ApplicationType.CABLE_REPORT)
                prop(HakemusEntity::hakemusEntityData)
                    .isInstanceOf(JohtoselvityshakemusEntityData::class)
                    .all {
                        prop(HakemusEntityData::name).isEqualTo(hakemusNimi)
                        prop(HakemusEntityData::applicationType)
                            .isEqualTo(ApplicationType.CABLE_REPORT)
                        prop(HakemusEntityData::pendingOnClient).isTrue()
                        prop(HakemusEntityData::areas).isNull()
                        prop(JohtoselvityshakemusEntityData::startTime).isNull()
                        prop(JohtoselvityshakemusEntityData::endTime).isNull()
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
                    hasNoObjectBefore()
                    hasObjectAfter<Hakemus> { prop(Hakemus::id).isEqualTo(hakemus.id) }
                }
            }
        }
    }

    @Nested
    inner class UpdateHakemus {

        @Nested
        inner class WithJohtoselvitys {

            private val intersectingArea =
                ApplicationFactory.createCableReportApplicationArea(
                    name = "area",
                    geometry =
                        "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json"
                            .asJsonResource())

            private val notInHankeArea =
                ApplicationFactory.createCableReportApplicationArea(
                    name = "area", geometry = GeometriaFactory.polygon())

            @Test
            fun `throws exception when the application does not exist`() {
                assertThat(hakemusRepository.findAll()).isEmpty()
                val request =
                    HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(1234, request, USERNAME)
                }

                exception.hasClass(HakemusNotFoundException::class)
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
                    hasClass(HakemusAlreadySentException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("alluId=21")
                }
            }

            @Test
            fun `does not create a new audit log entry when the application has not changed`() {
                val entity = hakemusFactory.builder().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
                // The saved hakemus has null in areas, but the response replaces it with an empty
                // list, so set the value back to null in the request.
                val request = hakemus.toUpdateRequest().withAreas(null)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                val applicationLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
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
                    hasClass(HakemusGeometryException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("reason=Self-intersection")
                    messageContains(
                        "location={\"type\":\"Point\",\"coordinates\":[25494009.65639264,6679886.142116806]}")
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
                    hasClass(HakemusGeometryNotInsideHankeException::class)
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
                            CustomerType.COMPANY, yhteystieto.id, kayttaja.id, newKayttaja.id)
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
                            CustomerType.COMPANY, tyonSuorittaja.id, hankekayttajaIds = arrayOf())

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
                            hankekayttajaIds = arrayOf(newKayttaja.id))

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
            fun `sends email to new contacts`() {
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
                        "Haitaton: Sinut on lisätty hakemukselle / Du har lagts till i en ansökan / You have been added to an application")
                assertThat(email.textBody())
                    .contains(
                        "laatimassa johtoselvityshakemusta hankkeelle \"${hanke.nimi}\" (${hanke.hankeTunnus})")
            }

            @Test
            fun `doesn't send email when the caller adds themself as contact`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val hakemus = hakemusFactory.builder(USERNAME, hanke).save()
                val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                assertThat(founder.permission?.userId).isNotNull().isEqualTo(USERNAME)
                val request =
                    hakemus
                        .toResponse()
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, null, founder.id)
                        .withContractor(CustomerType.COMPANY, null, founder.id)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(greenMail.receivedMessages).isEmpty()
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

            private val intersectingArea =
                createExcavationNotificationArea(
                    name = "area",
                    tyoalueet =
                        listOf(
                            createTyoalue(
                                "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json"
                                    .asJsonResource())),
                )

            private val notInHankeArea =
                createExcavationNotificationArea(
                    name = "area",
                    tyoalueet = listOf(createTyoalue(GeometriaFactory.polygon())),
                )

            @Test
            fun `throws exception when the application does not exist`() {
                assertThat(hakemusRepository.findAll()).isEmpty()
                val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(1234, request, USERNAME)
                }

                exception.hasClass(HakemusNotFoundException::class)
            }

            @Test
            fun `throws exception when the application has been sent to Allu`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                        .withStatus(alluId = 21)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest()

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(HakemusAlreadySentException::class)
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
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
                // The saved hakemus has null in areas, but the response replaces it with an empty
                // list,
                // so set the value back to null in the request.
                val request = hakemus.toUpdateRequest().withAreas(null)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                val applicationLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
                assertThat(applicationLogs).hasSize(originalAuditLogSize)
            }

            @Test
            fun `throws exception when there are invalid geometry in areas`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val request = hakemus.toUpdateRequest().withAreas(listOf(intersectingArea))

                val exception = assertFailure {
                    hakemusService.updateHakemus(hakemus.id, request, USERNAME)
                }

                exception.all {
                    hasClass(HakemusGeometryException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains("reason=Self-intersection")
                    messageContains(
                        "location={\"type\":\"Point\",\"coordinates\":[25494009.65639264,6679886.142116806]}")
                }
            }

            @Test
            fun `throws exception when the request has a persisted contact but the application does not`() {
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
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
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
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
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val requestHankekayttajaId = UUID.randomUUID()
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomerWithContactsRequest(
                            CustomerType.COMPANY, yhteystieto.id, requestHankekayttajaId)

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
                    hasClass(HakemusGeometryNotInsideHankeException::class)
                    messageContains("id=${hakemus.id}")
                    messageContains(hanke.logString())
                    messageContains(
                        "geometry=${notInHankeArea.tyoalueet.single().geometry.toJsonString()}")
                }
            }

            @Test
            fun `saves updated data and creates an audit log`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
                val hankeEntity = hankeRepository.findAll().single()
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                        .withWorkDescription("Old work description")
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id)
                val yhteystieto = hakemusyhteystietoRepository.findAll().first()
                val kayttaja = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
                val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
                val area =
                    createExcavationNotificationArea(hankealueId = hanke.alueet.single().id!!)
                val request =
                    hakemus
                        .toUpdateRequest()
                        .withCustomerWithContactsRequest(
                            CustomerType.COMPANY, yhteystieto.id, kayttaja.id, newKayttaja.id)
                        .withWorkDescription("New work description")
                        .withRequiredCompetence(true)
                        .withArea(area)

                val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(updatedHakemus.applicationData)
                    .isInstanceOf(KaivuilmoitusDataResponse::class)
                    .all {
                        prop(KaivuilmoitusDataResponse::workDescription)
                            .isEqualTo("New work description")
                        prop(KaivuilmoitusDataResponse::requiredCompetence).isTrue()
                        prop(KaivuilmoitusDataResponse::customerWithContacts)
                            .isNotNull()
                            .prop(CustomerWithContactsResponse::contacts)
                            .extracting { it.hankekayttajaId }
                            .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                        prop(KaivuilmoitusDataResponse::areas).isNotNull().single().isEqualTo(area)
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
                            CustomerType.COMPANY, tyonSuorittaja.id, hankekayttajaIds = arrayOf())

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
                            hankekayttajaIds = arrayOf(newKayttaja.id))

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
                        "Haitaton: Sinut on lisätty hakemukselle / Du har lagts till i en ansökan / You have been added to an application")
                assertThat(email.textBody())
                    .contains(
                        "laatimassa kaivuilmoitusta hankkeelle \"${hanke.nimi}\" (${hanke.hankeTunnus})")
            }

            @Test
            fun `doesn't send email when the caller adds themself as contact`() {
                val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
                val hakemus =
                    hakemusFactory
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .save()
                val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                assertThat(founder.permission?.userId).isNotNull().isEqualTo(USERNAME)
                val request =
                    hakemus
                        .toResponse()
                        .toUpdateRequest()
                        .withCustomer(CustomerType.COMPANY, null, founder.id)
                        .withContractor(CustomerType.COMPANY, null, founder.id)

                hakemusService.updateHakemus(hakemus.id, request, USERNAME)

                assertThat(greenMail.receivedMessages).isEmpty()
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

        private val areaOutsideDefaultHanke: JohtoselvitysHakemusalue =
            ApplicationFactory.createCableReportApplicationArea(
                geometry = GeometriaFactory.thirdPolygon())

        @Test
        fun `throws exception when the application doesn't exist`() {
            val failure = assertFailure { hakemusService.sendHakemus(1234, USERNAME) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `throws exception when the application has been sent before`() {
            val application =
                hakemusFactory.builder().withMandatoryFields().withStatus(alluId = alluId).save()

            val failure = assertFailure { hakemusService.sendHakemus(application.id, USERNAME) }

            failure.all {
                hasClass(HakemusAlreadySentException::class)
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
            assertThat(
                    geometriatDao.isInsideHankeAlueet(
                        hanke.id, areas.single().geometries().single()))
                .isFalse()
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
                    .toAlluCableReportData(hanke.hankeTunnus)
                    .copy(pendingOnClient = false)
            every { alluClient.create(applicationData) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            val responseApplicationData = response.applicationData as JohtoselvityshakemusData
            assertThat(responseApplicationData.pendingOnClient).isFalse()
            val savedApplication = hakemusRepository.findById(hakemus.id).get()
            val savedApplicationData =
                savedApplication.hakemusEntityData as JohtoselvityshakemusEntityData
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
                hasClass(HakemusAlreadySentException::class)
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
            val applicationData = application.hakemusEntityData as JohtoselvityshakemusEntityData
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
            assertThat(hakemusRepository.getReferenceById(application.id)).all {
                prop(HakemusEntity::alluid).isEqualTo(alluId)
                prop(HakemusEntity::hakemusEntityData).isEqualTo(expectedDataAfterSend)
                prop(HakemusEntity::applicationIdentifier).isNull()
                prop(HakemusEntity::alluStatus).isNull()
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
                hasClass(HakemusGeometryNotInsideHankeException::class)
                messageContains(hakemus.logString())
                messageContains(hanke.logString())
                messageContains(
                    "hakemus geometry=${areaOutsideDefaultHanke.geometry.toJsonString()}")
            }
        }

        @Test
        fun `cancels the sent application before throwing when uploading initial attachments fails`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
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
            val savedHakemus = hakemusRepository.getReferenceById(hakemus.id)
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
            val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
            attachmentFactory.save(application = applicationEntity).withContent()
            val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val hakemusData = hakemus.applicationData as JohtoselvityshakemusData
            val expectedDataAfterSend =
                hakemusData.copy(pendingOnClient = false).setOrdererForContractor(founder.id)
            val expectedAlluRequest =
                expectedDataAfterSend.toAlluCableReportData(hakemus.hankeTunnus)
            every { alluClient.create(expectedAlluRequest) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            justRun { alluClient.addAttachments(alluId, any(), any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            assertThat(response).all {
                prop(Hakemus::alluid).isEqualTo(alluId)
                prop(Hakemus::applicationIdentifier)
                    .isEqualTo(ApplicationFactory.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
            }
            assertThat(hakemusRepository.getReferenceById(hakemus.id)).all {
                prop(HakemusEntity::alluid).isEqualTo(alluId)
                prop(HakemusEntity::applicationIdentifier)
                    .isEqualTo(ApplicationFactory.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
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
            val expectedAlluRequest =
                expectedDataAfterSend.toAlluCableReportData(hakemus.hankeTunnus)
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
                    puhelinnumero = "999888777")
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
                        hasNoObjectAfter()
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
                        TYON_SUORITTAJA, expectedPerustajaContact.copy(orderer = false)),
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

        @Nested
        inner class Kaivuilmoitus {

            @Nested
            inner class ExistingJohtoselvitys {

                @Test
                fun `creates a new application to Allu and saves the ID and status to database`() {
                    val hanke = hankeFactory.builder().withHankealue().saveEntity()
                    val hankeAlueet = hankeService.loadHanke(hanke.hankeTunnus)!!.alueet
                    val hakemus: Hakemus =
                        hakemusFactory
                            .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                            .withMandatoryFields(hankeAlueet[0])
                            .save()
                    val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
                    attachmentFactory.save(application = applicationEntity).withContent()
                    val hakemusData = hakemus.applicationData as KaivuilmoitusData
                    val founder = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
                    val expectedDataAfterSend =
                        hakemusData
                            .copy(pendingOnClient = false)
                            .setOrdererForContractor(founder.id)
                    val expectedAlluRequest =
                        expectedDataAfterSend.toAlluExcavationNotificationData(hakemus.hankeTunnus)
                    every { alluClient.create(expectedAlluRequest) } returns alluId
                    justRun { alluClient.addAttachment(alluId, any()) }
                    justRun { alluClient.addAttachments(alluId, any(), any()) }
                    every { alluClient.getApplicationInformation(alluId) } returns
                        AlluFactory.createAlluApplicationResponse(
                            alluId, applicationId = DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)

                    val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

                    assertThat(response).all {
                        prop(Hakemus::alluid).isEqualTo(alluId)
                        prop(Hakemus::applicationIdentifier)
                            .isEqualTo(
                                ApplicationFactory.DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                        prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                        prop(Hakemus::applicationData).isEqualTo(expectedDataAfterSend)
                    }
                    assertThat(hakemusRepository.getReferenceById(hakemus.id)).all {
                        prop(HakemusEntity::alluid).isEqualTo(alluId)
                        prop(HakemusEntity::applicationIdentifier)
                            .isEqualTo(
                                ApplicationFactory.DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                        prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                    }
                    verifySequence {
                        alluClient.create(expectedAlluRequest)
                        alluClient.addAttachment(alluId, any())
                        alluClient.addAttachments(alluId, any(), any())
                        alluClient.getApplicationInformation(alluId)
                    }
                }
            }

            @Nested
            inner class AccompanyingJohtoselvitys {

                private var kaivuilmoitusHakemusId: Long = 0
                private val cableReportAlluId: Int = alluId
                private val excavationNotificationAlluId: Int = alluId + 1
                private lateinit var expectedCableReportAlluRequest: AlluCableReportApplicationData
                private lateinit var expectedExcavationNotificationAlluRequest:
                    AlluExcavationNotificationData
                private lateinit var expectedJohtoselvitysHakemusEntityData:
                    JohtoselvityshakemusEntityData
                private lateinit var expectedKaivuilmoitusDataAfterSend: KaivuilmoitusData

                /**
                 * Set up the test environment before each test. All the necessary mocks and data
                 * are set up here even though the mocked calls are not verified in all tests in
                 * order to keep the tests clean and readable.
                 */
                @BeforeEach
                fun setUp() {
                    val hanke = hankeFactory.builder("Other user").withHankealue().saveEntity()
                    val hankeAlueet = hankeService.loadHanke(hanke.hankeTunnus)!!.alueet
                    val currentUser =
                        hankeKayttajaFactory.saveIdentifiedUser(
                            hanke.id,
                            userId = USERNAME,
                            kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                        )
                    val hakemus =
                        hakemusFactory
                            .builder("Other user", hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                            .withMandatoryFields(hankeAlueet[0])
                            .withoutCableReports()
                            .withRockExcavation(false)
                            .asianhoitaja(currentUser)
                            .save()
                    kaivuilmoitusHakemusId = hakemus.id
                    val applicationEntity = hakemusRepository.getReferenceById(hakemus.id)
                    attachmentFactory.save(application = applicationEntity).withContent()
                    val hakemusData = hakemus.applicationData as KaivuilmoitusData
                    expectedJohtoselvitysHakemusEntityData =
                        (applicationEntity.hakemusEntityData as KaivuilmoitusEntityData)
                            .createAccompanyingJohtoselvityshakemusData()
                    val expectedJohtoselvityshakemusDataAfterSend =
                        expectedJohtoselvitysHakemusEntityData
                            .toHakemusData(hakemusData.yhteystiedotMap())
                            .setOrdererForRepresentative(currentUser.id)
                    expectedKaivuilmoitusDataAfterSend =
                        hakemusData
                            .copy(
                                pendingOnClient = false,
                                cableReports = listOf(DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER))
                            .setOrdererForRepresentative(currentUser.id)
                    expectedCableReportAlluRequest =
                        expectedJohtoselvityshakemusDataAfterSend.toAlluCableReportData(
                            hakemus.hankeTunnus)
                    expectedExcavationNotificationAlluRequest =
                        expectedKaivuilmoitusDataAfterSend.toAlluExcavationNotificationData(
                            hakemus.hankeTunnus)
                    every { alluClient.create(expectedCableReportAlluRequest) } returns
                        cableReportAlluId
                    every { alluClient.create(expectedExcavationNotificationAlluRequest) } returns
                        excavationNotificationAlluId
                    justRun { alluClient.addAttachment(cableReportAlluId, any()) }
                    justRun { alluClient.addAttachment(excavationNotificationAlluId, any()) }
                    justRun { alluClient.addAttachments(cableReportAlluId, any(), any()) }
                    justRun {
                        alluClient.addAttachments(excavationNotificationAlluId, any(), any())
                    }
                    every { alluClient.getApplicationInformation(cableReportAlluId) } returns
                        AlluFactory.createAlluApplicationResponse(cableReportAlluId)
                    every {
                        alluClient.getApplicationInformation(excavationNotificationAlluId)
                    } returns
                        AlluFactory.createAlluApplicationResponse(
                            excavationNotificationAlluId,
                            applicationId = DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                }

                /**
                 * Clear all mocks after each test. This is mandatory to do here in order to
                 * override the upper level @AfterEach method [checkMocks] which checks that all
                 * mocked calls were made and verifies their order. These checks cannot be done here
                 * because the calls described in [setUp] are not verified in all tests. Clearing
                 * the mocks has to be done nevertheless.
                 */
                @AfterEach
                fun clearMocks() {
                    clearAllMocks()
                }

                @Test
                fun `saves johtoselvitys to DB`() {
                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, USERNAME)

                    assertThat(hakemusRepository.getOneByAlluid(cableReportAlluId))
                        .isNotNull()
                        .all {
                            prop(HakemusEntity::alluid).isEqualTo(cableReportAlluId)
                            prop(HakemusEntity::applicationIdentifier)
                                .isEqualTo(
                                    ApplicationFactory.DEFAULT_CABLE_REPORT_APPLICATION_IDENTIFIER)
                            prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                            prop(HakemusEntity::hakemusEntityData)
                                .isEqualTo(expectedJohtoselvitysHakemusEntityData)
                            prop(HakemusEntity::userId).isEqualTo(USERNAME)
                        }
                }

                @Test
                fun `sends johtoselvitys and kaivuilmoitus to Allu`() {
                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, USERNAME)

                    verifySequence {
                        // first the cable report is sent
                        alluClient.create(expectedCableReportAlluRequest)
                        alluClient.addAttachment(cableReportAlluId, any())
                        alluClient.addAttachments(cableReportAlluId, any(), any())
                        alluClient.getApplicationInformation(cableReportAlluId)
                        // then the excavation notification is sent
                        alluClient.create(expectedExcavationNotificationAlluRequest)
                        alluClient.addAttachment(excavationNotificationAlluId, any())
                        alluClient.addAttachments(excavationNotificationAlluId, any(), any())
                        alluClient.getApplicationInformation(excavationNotificationAlluId)
                    }
                }

                @Test
                fun `returns the created kaivuilmoitus`() {
                    val response = hakemusService.sendHakemus(kaivuilmoitusHakemusId, USERNAME)

                    assertThat(response).all {
                        prop(Hakemus::alluid).isEqualTo(excavationNotificationAlluId)
                        prop(Hakemus::applicationIdentifier)
                            .isEqualTo(
                                ApplicationFactory.DEFAULT_EXCAVATION_NOTIFICATION_IDENTIFIER)
                        prop(Hakemus::alluStatus).isEqualTo(ApplicationStatus.PENDING)
                        prop(Hakemus::applicationData).isEqualTo(expectedKaivuilmoitusDataAfterSend)
                    }
                }

                @Test
                fun `writes the created hakemus to audit logs`() {
                    auditLogRepository.deleteAll()

                    hakemusService.sendHakemus(kaivuilmoitusHakemusId, USERNAME)

                    val logs = auditLogRepository.findByType(ObjectType.HAKEMUS)
                    assertThat(logs).single().isSuccess(Operation.CREATE) {
                        hasUserActor(USERNAME)
                        withTarget {
                            hasTargetType(ObjectType.HAKEMUS)
                            hasNoObjectBefore()
                            hasObjectAfter<Hakemus> {
                                prop(Hakemus::applicationType)
                                    .isEqualTo(ApplicationType.CABLE_REPORT)
                            }
                        }
                    }
                }

                fun HakemusData.yhteystiedotMap(): Map<ApplicationContactType, Hakemusyhteystieto> =
                    this.yhteystiedot().associateBy { it.rooli }
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

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = true))
            assertThat(hakemusRepository.findAll()).isEmpty()
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
                        ApplicationAttachmentContentService.prefix(hakemus.id)))
                .hasSize(2)

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = true))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id)))
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).isEmpty()
        }

        @Test
        fun `deletes all attachment metadata even when deleting attachment content fails`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = hakemus).withContent()
            attachmentFactory.save(application = hakemus).withContent()
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).hasSize(2)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id)))
                .hasSize(2)
            fileClient.connected = false

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            fileClient.connected = true
            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = true))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(hakemus.id)))
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(hakemus.id)).isEmpty()
        }

        @Test
        fun `doesn't delete hanke when hanke is not generated`() {
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = false))
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)).isNotNull()
        }

        @Test
        fun `doesn't delete hanke when a generated hanke has other applications`() {
            val hanke = hankeFactory.saveMinimal(generated = true)
            val hakemus1 = hakemusFactory.builder(hanke).withNoAlluFields().saveEntity()
            val hakemus2 = hakemusFactory.builder(hanke).withNoAlluFields().saveEntity()
            assertThat(hakemusRepository.findAll()).hasSize(2)

            val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(hakemus1.id, USERNAME)

            assertThat(result).isEqualTo(HakemusDeletionResultDto(hankeDeleted = false))
            assertThat(hakemusRepository.findAll())
                .single()
                .prop(HakemusEntity::id)
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

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
        }
    }

    @Nested
    inner class ReportOperationalCondition {
        private val date: LocalDate = LocalDate.parse("2024-08-13")
        private val startDate: ZonedDateTime = ZonedDateTime.parse("2024-08-08T00:00Z")
        private val endDate: ZonedDateTime = ZonedDateTime.parse("2024-08-12T00:00Z")

        @Test
        fun `throws exception when hakemus is not found`() {
            val failure = assertFailure { hakemusService.reportOperationalCondition(414L, date) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("with id 414")
            }
        }

        @Test
        fun `throws exception when hakemus is not in Allu`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withNoAlluFields()
                    .save()

            val failure = assertFailure {
                hakemusService.reportOperationalCondition(hakemus.id, date)
            }

            failure.all {
                hasClass(HakemusNotYetInAlluException::class)
                messageContains("Hakemus is not yet in Allu")
                messageContains("id=${hakemus.id}")
            }
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationType::class,
            names = ["EXCAVATION_NOTIFICATION"],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `throws exception when hakemus is of wrong type`(type: ApplicationType) {
            val hakemus = hakemusFactory.builder(type).withStatus().save()

            val failure = assertFailure {
                hakemusService.reportOperationalCondition(hakemus.id, date)
            }

            failure.all {
                hasClass(WrongHakemusTypeException::class)
                messageContains("Wrong application type for this action")
                messageContains("type=$type")
                messageContains("allowed types=EXCAVATION_NOTIFICATION")
                messageContains("id=${hakemus.id}")
            }
        }

        @Test
        fun `sends the date to Allu when the hakemus passes all checks`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.PENDING)
                    .withStartTime(startDate)
                    .withEndTime(endDate)
                    .save()
            justRun { alluClient.reportOperationalCondition(hakemus.alluid!!, date) }

            hakemusService.reportOperationalCondition(hakemus.id, date)

            verifySequence { alluClient.reportOperationalCondition(hakemus.alluid!!, date) }
        }

        @Test
        fun `saves the report event to database`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withMandatoryFields()
                    .withStatus(ApplicationStatus.PENDING)
                    .withStartTime(startDate)
                    .withEndTime(endDate)
                    .save()
            justRun { alluClient.reportOperationalCondition(hakemus.alluid!!, date) }

            hakemusService.reportOperationalCondition(hakemus.id, date)

            val savedHakemus: Hakemus = hakemusService.getById(hakemus.id)
            assertThat(savedHakemus.valmistumisilmoitukset)
                .key(ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO)
                .single()
                .all {
                    prop(Valmistumisilmoitus::type)
                        .isEqualTo(ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO)
                    prop(Valmistumisilmoitus::dateReported).isEqualTo(date)
                    prop(Valmistumisilmoitus::createdAt).isRecent()
                    prop(Valmistumisilmoitus::hakemustunnus)
                        .isEqualTo(hakemus.applicationIdentifier)
                }
            verifySequence { alluClient.reportOperationalCondition(hakemus.alluid!!, date) }
        }
    }

    @Nested
    inner class DownloadDecision {
        private val alluId = 134
        private val decisionPdf: ByteArray =
            "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()

        @Test
        fun `when unknown ID should throw`() {
            val failure = assertFailure { hakemusService.downloadDecision(1234) }

            failure.all {
                hasClass(HakemusNotFoundException::class)
                messageContains("id 1234")
            }
        }

        @Test
        fun `when no alluid should throw`() {
            val hakemus = hakemusFactory.builder().withNoAlluFields().save()

            val failure = assertFailure { hakemusService.downloadDecision(hakemus.id) }

            failure.all {
                hasClass(HakemusDecisionNotFoundException::class)
                messageContains("id=${hakemus.id}")
            }
            verify { alluClient wasNot Called }
        }

        @Test
        fun `when no decision in Allu should throw`() {
            val hakemus = hakemusFactory.builder().inHandling(alluId = alluId).save()
            every { alluClient.getDecisionPdf(alluId) }.throws(HakemusDecisionNotFoundException(""))

            val failure = assertFailure { hakemusService.downloadDecision(hakemus.id) }

            failure.hasClass(HakemusDecisionNotFoundException::class)
            verify { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `when decision exists should return it`() {
            val hakemus = hakemusFactory.builder().inHandling(alluId = alluId).save()
            every { alluClient.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = hakemusService.downloadDecision(hakemus.id)

            assertThat(filename).isNotNull().isEqualTo(hakemus.applicationIdentifier)
            assertThat(bytes).isEqualTo(decisionPdf)
            verify { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `when application has no identifier should use default file name`() {
            val hakemus =
                hakemusFactory.builder().withStatus(alluId = alluId, identifier = null).save()
            every { alluClient.getDecisionPdf(alluId) }.returns(decisionPdf)

            val (filename, bytes) = hakemusService.downloadDecision(hakemus.id)

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
                        ApplicationAttachmentContentService.prefix(application.id)))
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .hasSize(2)

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id)))
                .isEmpty()
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .isEmpty()
        }

        @Test
        fun `deletes application attachment metadata even when attachment content deletion fails`() {
            val application = hakemusFactory.builder().withNoAlluFields().saveEntity()
            attachmentFactory.save(application = application).withContent()
            attachmentFactory.save(application = application).withContent()
            val hakemus = hakemusService.getById(application.id)
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id)))
                .hasSize(2)
            assertThat(applicationAttachmentRepository.findByApplicationId(application.id))
                .hasSize(2)
            fileClient.connected = false

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            fileClient.connected = true
            assertThat(
                    fileClient.list(
                        Container.HAKEMUS_LIITTEET,
                        ApplicationAttachmentContentService.prefix(application.id)))
                .hasSize(2)
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
                    hasNoObjectAfter()
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

            assertThat(hakemusRepository.findAll()).isEmpty()
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
                hasClass(HakemusAlreadyProcessingException::class)
                messageContains("id=${hakemus.id}")
                messageContains("alluId=$alluId")
            }
            assertThat(hakemusRepository.findAll()).hasSize(1)
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

            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(hakemusyhteystietoRepository.findAll()).isEmpty()
            assertThat(hakemusyhteyshenkiloRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.count())
                .isEqualTo(5) // Hanke founder + one kayttaja for each role
        }

        @Test
        fun `deletes application when application is already cancelled`() {
            val application =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.CANCELLED, alluId)
                    .saveEntity()
            val hakemus = hakemusService.getById(application.id)

            hakemusService.cancelAndDelete(hakemus, USERNAME)

            assertThat(hakemusRepository.findAll()).isEmpty()
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
                    status = status ?: ApplicationStatus.PENDING)

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
            names = ["PENDING", "PENDING_CLIENT"])
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
        private val identifier = ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER

        @Test
        fun `updates the last updated time with empty histories`() {
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)

            hakemusService.handleHakemusUpdates(listOf(), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
        }

        @Test
        fun `updates the hakemus statuses in the correct order`() {
            hakemusFactory.builder(USERNAME).withStatus(alluId = alluId).save()
            val firstEventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z")
            val history =
                ApplicationHistoryFactory.create(
                    alluId,
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime.plusDays(5), ApplicationStatus.PENDING),
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime.plusDays(10), ApplicationStatus.HANDLING),
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime, ApplicationStatus.PENDING),
                )

            hakemusService.handleHakemusUpdates(listOf(history), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
            val application = hakemusRepository.getOneByAlluid(alluId)
            assertThat(application)
                .isNotNull()
                .prop("alluStatus", HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(application!!.applicationIdentifier).isEqualTo(identifier)
        }

        @Test
        fun `doesn't update status or identifier when the update status is REPLACED`() {
            val originalTunnus = "JS2400001-12"
            hakemusFactory
                .builder(USERNAME)
                .withStatus(
                    alluId = alluId,
                    status = ApplicationStatus.DECISION,
                    identifier = originalTunnus,
                )
                .save()
            val history =
                ApplicationHistoryFactory.create(
                    alluId,
                    ApplicationHistoryFactory.createEvent(
                        applicationIdentifier = "JS2400001-13",
                        newStatus = ApplicationStatus.REPLACED,
                    ),
                )

            hakemusService.handleHakemusUpdates(listOf(history), updateTime)

            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.DECISION)
                prop(HakemusEntity::applicationIdentifier).isEqualTo(originalTunnus)
            }
        }

        @Test
        fun `ignores missing hakemus`() {
            assertThat(hakemusRepository.findAll()).isEmpty()
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
            val applications = hakemusRepository.findAll()
            assertThat(applications).hasSize(2)
            assertThat(applications.map { it.alluid }).containsExactlyInAnyOrder(alluId, alluId + 2)
            assertThat(applications.map { it.alluStatus })
                .containsExactlyInAnyOrder(
                    ApplicationStatus.PENDING_CLIENT, ApplicationStatus.PENDING_CLIENT)
            assertThat(applications.map { it.applicationIdentifier })
                .containsExactlyInAnyOrder("JS2300082", "JS2300084")
        }

        @Test
        fun `sends email to the contacts when a johtoselvityshakemus gets a decision`() {
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
                            newStatus = ApplicationStatus.DECISION)),
                )

            hakemusService.handleHakemusUpdates(histories, updateTime)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys $identifier / Ledningsutredning $identifier / Cable report $identifier")
        }

        @ParameterizedTest
        @EnumSource(ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION"])
        fun `sends email to the contacts when a kaivuilmoitus gets a decision`(
            applicationStatus: ApplicationStatus
        ) {
            val identifier = "KP2300001"
            val hanke = hankeFactory.saveMinimal()
            val hakija = hankeKayttajaFactory.saveUser(hankeId = hanke.id)
            hakemusFactory
                .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .hakija(hakija)
                .saveEntity()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluId,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = applicationStatus,
                        ),
                    ),
                )
            every {
                when (applicationStatus) {
                    ApplicationStatus.DECISION -> alluClient.getDecisionPdf(alluId)
                    ApplicationStatus.OPERATIONAL_CONDITION ->
                        alluClient.getOperationalConditionPdf(alluId)
                    else -> throw IllegalArgumentException("Invalid application status")
                }
            } returns PDF_BYTES
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId, applicationId = identifier)

            hakemusService.handleHakemusUpdates(histories, updateTime)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa")
            verifySequence {
                when (applicationStatus) {
                    ApplicationStatus.DECISION -> alluClient.getDecisionPdf(alluId)
                    ApplicationStatus.OPERATIONAL_CONDITION ->
                        alluClient.getOperationalConditionPdf(alluId)
                    else -> throw IllegalArgumentException("Invalid application status")
                }
                alluClient.getApplicationInformation(alluId)
            }
        }

        private fun mockAlluDownload(status: ApplicationStatus) =
            when (status) {
                ApplicationStatus.DECISION ->
                    every { alluClient.getDecisionPdf(alluId) } returns PDF_BYTES
                ApplicationStatus.OPERATIONAL_CONDITION ->
                    every { alluClient.getOperationalConditionPdf(alluId) } returns PDF_BYTES
                ApplicationStatus.FINISHED ->
                    every { alluClient.getWorkFinishedPdf(alluId) } returns PDF_BYTES
                else -> throw IllegalArgumentException()
            }

        private fun verifyAlluDownload(status: ApplicationStatus) =
            when (status) {
                ApplicationStatus.DECISION -> verify { alluClient.getDecisionPdf(alluId) }
                ApplicationStatus.OPERATIONAL_CONDITION ->
                    verify { alluClient.getOperationalConditionPdf(alluId) }
                ApplicationStatus.FINISHED -> verify { alluClient.getWorkFinishedPdf(alluId) }
                else -> throw IllegalArgumentException()
            }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
        fun `downloads the document when a kaivuilmoitus gets a decision`(
            status: ApplicationStatus
        ) {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                    .save()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluId,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = status,
                        )),
                )
            mockAlluDownload(status)
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse()

            hakemusService.handleHakemusUpdates(histories, updateTime)

            assertThat(fileClient.listBlobs(Container.PAATOKSET))
                .single()
                .prop(TestFile::path)
                .startsWith("${hakemus.id}/")
            verifyAlluDownload(status)
            verify { alluClient.getApplicationInformation(alluId) }
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

private fun JohtoselvityshakemusData.setOrdererForRepresentative(
    kayttajaId: UUID
): JohtoselvityshakemusData =
    this.copy(representativeWithContacts = representativeWithContacts!!.setOrderer(kayttajaId))

private fun KaivuilmoitusData.setOrdererForContractor(kayttajaId: UUID): KaivuilmoitusData =
    this.copy(contractorWithContacts = contractorWithContacts!!.setOrderer(kayttajaId))

private fun KaivuilmoitusData.setOrdererForRepresentative(kayttajaId: UUID): KaivuilmoitusData =
    this.copy(representativeWithContacts = representativeWithContacts!!.setOrderer(kayttajaId))

private fun Hakemusyhteystieto.setOrderer(kayttajaId: UUID): Hakemusyhteystieto {
    val yhteyshenkilot =
        yhteyshenkilot.map { if (it.hankekayttajaId == kayttajaId) it.copy(tilaaja = true) else it }

    return copy(yhteyshenkilot = yhteyshenkilot)
}
