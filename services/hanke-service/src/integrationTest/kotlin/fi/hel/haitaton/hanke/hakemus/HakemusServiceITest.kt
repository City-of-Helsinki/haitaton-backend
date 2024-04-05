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
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluException
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.toUpdateRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withArea
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withAreas
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withContractor
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomer
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomerWithContactsRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluData
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HakemusServiceITest(
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val geometriatDao: GeometriatDao,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val attachmentFactory: ApplicationAttachmentFactory,
    @Autowired private val cableReportService: CableReportService,
) : IntegrationTest() {

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(cableReportService)
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
                            ApplicationContactType.HAKIJA to true,
                            ApplicationContactType.TYON_SUORITTAJA to false,
                            ApplicationContactType.RAKENNUTTAJA to false,
                            ApplicationContactType.ASIANHOITAJA to false,
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                    messageContains("role=${ApplicationContactType.HAKIJA}")
                    messageContains("yhteystietoId=null")
                    messageContains("newId=$requestYhteystietoId")
                }
            }

            @Test
            fun `throws exception when the request has different persisted contact than the application`() {
                val entity = hakemusFactory.builder().hakija().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                    messageContains("role=${ApplicationContactType.HAKIJA}")
                    messageContains("yhteystietoId=$originalYhteystietoId")
                    messageContains("newId=$requestYhteystietoId")
                }
            }

            @Test
            fun `throws exception when the request has a contact that is not a user on hanke`() {
                val entity = hakemusFactory.builder().hakija().saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
                val request = hakemus.toUpdateRequest().withArea(notInHankeArea)

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
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                    hakemusyhteystietoRepository.findAll().single {
                        it.rooli == ApplicationContactType.TYON_SUORITTAJA
                    }
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .saveEntity()
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
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .hakija()
                        .saveEntity()
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
                val entity =
                    hakemusFactory
                        .builder(
                            userId = USERNAME,
                            applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                        )
                        .hakija()
                        .saveEntity()
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
                val entity =
                    hakemusFactory
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .saveEntity()
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
                        .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                        .withWorkDescription("Old work description")
                        .hakija()
                        .saveEntity()
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
                val tyonSuorittaja =
                    hakemusyhteystietoRepository.findAll().single {
                        it.rooli == ApplicationContactType.TYON_SUORITTAJA
                    }
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
                val hakemus = hakemusService.hakemusResponse(entity.id!!)
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
            every { cableReportService.create(any()) } returns alluId
            every { cableReportService.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)

            hakemusService.sendHakemus(application.id, USERNAME)

            verifySequence {
                cableReportService.create(any())
                cableReportService.getApplicationInformation(any())
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
            every { cableReportService.create(applicationData) } returns alluId
            every { cableReportService.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId)

            val response = hakemusService.sendHakemus(hakemus.id, USERNAME)

            val responseApplicationData = response.applicationData as JohtoselvityshakemusData
            Assertions.assertFalse(responseApplicationData.pendingOnClient)
            val savedApplication = applicationRepository.findById(hakemus.id).get()
            val savedApplicationData =
                savedApplication.applicationData as CableReportApplicationData
            Assertions.assertFalse(savedApplicationData.pendingOnClient)
            verifySequence {
                cableReportService.create(applicationData)
                cableReportService.getApplicationInformation(alluId)
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

            val failure = assertFailure { hakemusService.sendHakemus(application.id!!, USERNAME) }

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
            every { cableReportService.create(any()) } returns alluId
            every { cableReportService.getApplicationInformation(alluId) } throws
                AlluException(listOf())

            val response = hakemusService.sendHakemus(application.id!!, USERNAME)

            assertThat(response).all {
                prop(Hakemus::alluid).isEqualTo(alluId)
                prop(Hakemus::applicationIdentifier).isNull()
                prop(Hakemus::alluStatus).isNull()
            }
            val savedApplication = applicationRepository.findById(application.id!!).get()
            Assertions.assertEquals(alluId, savedApplication.alluid)
            Assertions.assertEquals(expectedDataAfterSend, savedApplication.applicationData)
            Assertions.assertNull(savedApplication.applicationIdentifier)
            Assertions.assertNull(savedApplication.alluStatus)

            verifySequence {
                cableReportService.create(any())
                cableReportService.getApplicationInformation(alluId)
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
                messageContains("hakemusId=${hakemus.id}")
                messageContains(hanke.logString())
                messageContains(
                    "application geometry=${areaOutsideDefaultHanke.geometry.toJsonString()}"
                )
            }
        }

        @Test
        fun `cancels the sent application before throwing when uploading initial attachments fails`() {
            val hakemus = hakemusFactory.builder().withMandatoryFields().save()
            val applicationEntity = applicationRepository.getReferenceById(hakemus.id)
            attachmentFactory.save(application = applicationEntity).withContent()
            every { cableReportService.create(any()) } returns alluId
            every { cableReportService.addAttachments(alluId, any(), any()) } throws
                AlluException(listOf())
            justRun { cableReportService.cancel(alluId) }
            every { cableReportService.sendSystemComment(alluId, any()) } returns 4141

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, USERNAME) }

            failure.hasClass(AlluException::class)
            verifySequence {
                cableReportService.create(any())
                cableReportService.addAttachments(alluId, any(), any())
                cableReportService.cancel(alluId)
                cableReportService.sendSystemComment(
                    alluId,
                    ALLU_INITIAL_ATTACHMENT_CANCELLATION_MSG
                )
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
            every { cableReportService.create(expectedAlluRequest) } returns alluId
            justRun { cableReportService.addAttachments(alluId, any(), any()) }
            every { cableReportService.getApplicationInformation(alluId) } returns
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
                cableReportService.create(expectedAlluRequest)
                cableReportService.addAttachments(alluId, any(), any())
                cableReportService.getApplicationInformation(alluId)
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
            every { cableReportService.create(expectedAlluRequest) } returns alluId
            every { cableReportService.getApplicationInformation(alluId) } returns
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
                cableReportService.create(expectedAlluRequest)
                cableReportService.getApplicationInformation(alluId)
            }
        }

        @Test
        fun `throws exception when sender is not a contact`() {
            val hakemus = hakemusFactory.builder(userId = "Other user").withMandatoryFields().save()

            val failure = assertFailure { hakemusService.sendHakemus(hakemus.id, USERNAME) }

            failure.all {
                hasClass(UserNotInContactsException::class)
                messageContains("applicationId=${hakemus.id}")
                messageContains("userId=$USERNAME")
            }
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
