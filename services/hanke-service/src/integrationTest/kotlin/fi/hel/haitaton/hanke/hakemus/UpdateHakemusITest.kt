package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.email.textBody
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createExcavationNotificationArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.createTyoalue
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.toUpdateRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withArea
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withAreas
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withContractor
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomer
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withCustomerWithContactsRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withDates
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withInvoicingCustomer
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withName
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withRequiredCompetence
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.tormaystarkastelu.Autoliikenneluokittelu
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class UpdateHakemusITest(
    @Autowired private val hakemusService: HakemusService,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val alluClient: AlluClient,
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
    inner class WithJohtoselvitys {

        private val intersectingArea =
            ApplicationFactory.createCableReportApplicationArea(
                name = "area",
                geometry =
                    "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json".asJsonResource(),
            )

        private val notInHankeArea =
            ApplicationFactory.createCableReportApplicationArea(
                name = "area",
                geometry = GeometriaFactory.polygon(),
            )

        @Test
        fun `throws exception when the application has been sent to Allu`() {
            val hakemus = hakemusFactory.builder().withStatus(alluId = 21).save()
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
            val hakemus = hakemusFactory.builder().save()
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
            val hakemus = hakemusFactory.builder().save()
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
            val hakemus = hakemusFactory.builder().save()
            val requestYhteystietoId = UUID.randomUUID()
            val request =
                hakemus.toUpdateRequest().withCustomer(CustomerType.COMPANY, requestYhteystietoId)

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
            val hakemus = hakemusFactory.builder().hakija().save()
            val originalYhteystietoId = hakemusyhteystietoRepository.findAll().first().id
            val requestYhteystietoId = UUID.randomUUID()
            val request =
                hakemus.toUpdateRequest().withCustomer(CustomerType.COMPANY, requestYhteystietoId)

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
            val hakemus = hakemusFactory.builder().hakija().save()
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
            val hakemus = hakemusFactory.builder(hanke).save()
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
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke)
                    .withWorkDescription("Old work description")
                    .hakija()
                    .save()
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val kayttaja = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, yhteystieto.id, kayttaja.id, newKayttaja.id)
                    .withWorkDescription("New work description")

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .all {
                    prop(JohtoselvityshakemusData::workDescription)
                        .isEqualTo("New work description")
                    prop(JohtoselvityshakemusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                }
            val applicationLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
            assertThat(applicationLogs).hasSize(originalAuditLogSize + 1)

            val persistedHakemus = hakemusService.getById(updatedHakemus.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .all {
                    prop(JohtoselvityshakemusData::workDescription)
                        .isEqualTo("New work description")
                    prop(JohtoselvityshakemusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                }
        }

        @Test
        fun `removes existing yhteyshenkilot from an yhteystieto`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val kayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
            val kayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke)
                    .hakija()
                    .tyonSuorittaja(kayttaja1, kayttaja2)
                    .save()
            val tyonSuorittaja =
                hakemusyhteystietoRepository.findAll().single { it.rooli == TYON_SUORITTAJA }
            val request =
                hakemus
                    .toUpdateRequest()
                    .withContractor(
                        CustomerType.COMPANY,
                        tyonSuorittaja.id,
                        hankekayttajaIds = arrayOf(),
                    )

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::contractorWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .isEmpty()

            val persistedHakemus = hakemusService.getById(updatedHakemus.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::contractorWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .isEmpty()
        }

        @Test
        fun `adds a new yhteystieto and an yhteyshenkilo for it at the same time`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke)
                    .withWorkDescription("Old work description")
                    .save()
            assertThat(hakemus.applicationData.customerWithContacts).isNull()
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.COMPANY,
                        yhteystietoId = null,
                        hankekayttajaIds = arrayOf(newKayttaja.id),
                    )

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { it.hankekayttajaId }
                .containsExactly(newKayttaja.id)

            val persistedHakemus = hakemusService.getById(updatedHakemus.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { it.hankekayttajaId }
                .containsExactly(newKayttaja.id)
        }

        @Test
        fun `sends email to new contacts`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hakemus = hakemusFactory.builder(USERNAME, hanke).hakija().save()
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, yhteystieto.id, newKayttaja.id)
                    .withContractor(CustomerType.COMPANY, null, newKayttaja.id)

            hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients.single().toString()).isEqualTo(newKayttaja.sahkoposti)
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
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, null, founder.id)
                    .withContractor(CustomerType.COMPANY, null, founder.id)

            hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(greenMail.receivedMessages).isEmpty()
        }

        @Test
        fun `updates project name when application name is changed`() {
            val hakemus = hakemusFactory.builderWithGeneratedHanke().save()
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

            val exception = assertFailure { hakemusService.updateHakemus(1234, request, USERNAME) }

            exception.hasClass(HakemusNotFoundException::class)
        }

        @Test
        fun `throws exception when the application has been sent to Allu`() {
            val hakemus =
                hakemusFactory
                    .builder(
                        userId = USERNAME,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    )
                    .withStatus(alluId = 21)
                    .save()
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
            val hakemus =
                hakemusFactory
                    .builder(
                        userId = USERNAME,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    )
                    .save()
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
            val hakemus =
                hakemusFactory
                    .builder(
                        userId = USERNAME,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    )
                    .save()
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
            val hakemus =
                hakemusFactory
                    .builder(
                        userId = USERNAME,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    )
                    .save()
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
            val hakemus =
                hakemusFactory
                    .builder(
                        userId = USERNAME,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    )
                    .hakija()
                    .save()
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
            val hakemus =
                hakemusFactory
                    .builder(
                        userId = USERNAME,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    )
                    .hakija()
                    .save()
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val requestHankekayttajaId = UUID.randomUUID()
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(
                        CustomerType.COMPANY,
                        yhteystieto.id,
                        requestHankekayttajaId,
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
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .save()
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
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withWorkDescription("Old work description")
                    .hakija()
                    .save()
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val kayttaja = hankeKayttajaFactory.getFounderFromHakemus(hakemus.id)
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val originalAuditLogSize = auditLogRepository.findByType(ObjectType.HAKEMUS).size
            val area = createExcavationNotificationArea(hankealueId = hanke.alueet.single().id!!)
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(
                        CustomerType.COMPANY,
                        yhteystieto.id,
                        kayttaja.id,
                        newKayttaja.id,
                    )
                    .withWorkDescription("New work description")
                    .withRequiredCompetence(true)
                    .withDates(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1))
                    .withArea(area)

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData).isInstanceOf(KaivuilmoitusData::class).all {
                prop(KaivuilmoitusData::workDescription).isEqualTo("New work description")
                prop(KaivuilmoitusData::requiredCompetence).isTrue()
                prop(KaivuilmoitusData::customerWithContacts)
                    .isNotNull()
                    .prop(Hakemusyhteystieto::yhteyshenkilot)
                    .extracting { it.hankekayttajaId }
                    .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                prop(KaivuilmoitusData::areas)
                    .isNotNull()
                    .single()
                    .transform { it.withoutTormaystarkastelut() }
                    .isEqualTo(area.withoutTormaystarkastelut())
            }

            val applicationLogs = auditLogRepository.findByType(ObjectType.HAKEMUS)
            assertThat(applicationLogs).hasSize(originalAuditLogSize + 1)

            val persistedHakemus = hakemusService.getById(updatedHakemus.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .all {
                    prop(KaivuilmoitusData::workDescription).isEqualTo("New work description")
                    prop(KaivuilmoitusData::requiredCompetence).isTrue()
                    prop(KaivuilmoitusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(kayttaja.id, newKayttaja.id)
                    prop(KaivuilmoitusData::areas)
                        .isNotNull()
                        .single()
                        .transform { it.withoutTormaystarkastelut() }
                        .isEqualTo(area.withoutTormaystarkastelut())
                }
        }

        @Test
        fun `removes existing yhteyshenkilot from an yhteystieto`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val kayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
            val kayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .hakija()
                    .tyonSuorittaja(kayttaja1, kayttaja2)
                    .save()
            val tyonSuorittaja =
                hakemusyhteystietoRepository.findAll().single { it.rooli == TYON_SUORITTAJA }
            val request =
                hakemus
                    .toUpdateRequest()
                    .withContractor(
                        CustomerType.COMPANY,
                        tyonSuorittaja.id,
                        hankekayttajaIds = arrayOf(),
                    )

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::contractorWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .isEmpty()
        }

        @Test
        fun `adds a new yhteystieto and an yhteyshenkilo for it at the same time`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .withWorkDescription("Old work description")
                    .save()
            assertThat(hakemus.applicationData.customerWithContacts).isNull()
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.COMPANY,
                        yhteystietoId = null,
                        hankekayttajaIds = arrayOf(newKayttaja.id),
                    )

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { it.hankekayttajaId }
                .containsExactly(newKayttaja.id)

            val persistedHakemus = hakemusService.getById(updatedHakemus.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { it.hankekayttajaId }
                .containsExactly(newKayttaja.id)
        }

        @Test
        fun `throws exception when an existing yhteystieto has registry key hidden but different type in the request`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .hakija(HakemusyhteystietoFactory.create(tyyppi = CustomerType.COMPANY))
                    .save()
            val yhteystietoId = hakemus.applicationData.customerWithContacts!!.id
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.PERSON,
                        yhteystietoId,
                        registryKey = null,
                        registryKeyHidden = true,
                    )

            val failure = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            failure.all {
                hasClass(InvalidHiddenRegistryKey::class)
                messageContains("id=${hakemus.id}")
                messageContains("RegistryKeyHidden used in an incompatible way")
                messageContains("New customer type doesn't match the old")
            }
        }

        @ParameterizedTest
        @ValueSource(strings = ["Something completely different"])
        @NullSource
        fun `keeps old customer registry key when registry key hidden is true`(
            newRegistryKey: String?
        ) {
            val henkilotunnus = "090626-885W"
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .hakija(
                        HakemusyhteystietoFactory.create(
                            tyyppi = CustomerType.PERSON,
                            registryKey = henkilotunnus,
                        ))
                    .save()
            val yhteystietoId = hakemus.applicationData.customerWithContacts!!.id
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.PERSON,
                        yhteystietoId,
                        registryKey = newRegistryKey,
                        registryKeyHidden = true,
                    )

            val result = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(result.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::registryKey)
                .isEqualTo(henkilotunnus)
            val persistedHakemus = hakemusService.getById(result.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::registryKey)
                .isEqualTo(henkilotunnus)
        }

        @Test
        fun `throws exception when an existing laskutusyhteystieto has registry key hidden but different type in the request`() {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withInvoicingCustomer(ApplicationFactory.createCompanyInvoicingCustomer())
                    .save()
            val request =
                (hakemus.toUpdateRequest() as KaivuilmoitusUpdateRequest).withInvoicingCustomer(
                    CustomerType.PERSON,
                    registryKey = null,
                    registryKeyHidden = true,
                )

            val failure = assertFailure {
                hakemusService.updateHakemus(hakemus.id, request, USERNAME)
            }

            failure.all {
                hasClass(InvalidHiddenRegistryKey::class)
                messageContains("id=${hakemus.id}")
                messageContains("RegistryKeyHidden used in an incompatible way")
                messageContains("New invoicing customer type doesn't match the old")
            }
        }

        @ParameterizedTest
        @ValueSource(strings = ["Something completely different"])
        @NullSource
        fun `keeps old invoicing customer registry key when registry key hidden is true`(
            newRegistryKey: String?
        ) {
            val henkilotunnus = ApplicationFactory.DEFAULT_HENKILOTUNNUS
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withInvoicingCustomer(ApplicationFactory.createPersonInvoicingCustomer())
                    .save()
            val request =
                (hakemus.toUpdateRequest() as KaivuilmoitusUpdateRequest).withInvoicingCustomer(
                    CustomerType.PERSON,
                    registryKey = newRegistryKey,
                    registryKeyHidden = true,
                )

            val result = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(result.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::invoicingCustomer)
                .isNotNull()
                .prop(Laskutusyhteystieto::registryKey)
                .isEqualTo(henkilotunnus)
            val persistedHakemus = hakemusService.getById(result.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::invoicingCustomer)
                .isNotNull()
                .prop(Laskutusyhteystieto::registryKey)
                .isEqualTo(henkilotunnus)
        }

        @Test
        fun `sends email for new contacts`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .hakija()
                    .save()
            val yhteystieto = hakemusyhteystietoRepository.findAll().first()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val request =
                hakemus
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, yhteystieto.id, newKayttaja.id)
                    .withContractor(CustomerType.COMPANY, null, newKayttaja.id)

            hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients.single().toString()).isEqualTo(newKayttaja.sahkoposti)
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
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, null, founder.id)
                    .withContractor(CustomerType.COMPANY, null, founder.id)

            hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(greenMail.receivedMessages).isEmpty()
        }

        @Test
        fun `updates project name when application name is changed`() {
            val hakemus =
                hakemusFactory
                    .builderWithGeneratedHanke(tyyppi = ApplicationType.EXCAVATION_NOTIFICATION)
                    .save()
            val request = hakemus.toUpdateRequest().withName("New name")

            hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(hankeRepository.findAll().single()).all {
                prop(HankeEntity::nimi).isEqualTo("New name")
            }
        }

        @Test
        fun `calculates tormaystarkastelu for work areas`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.findAll().single()
            val entity =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            val hakemus = hakemusService.getById(entity.id)
            val area = createExcavationNotificationArea(hankealueId = hanke.alueet.single().id!!)
            val request =
                hakemus
                    .toUpdateRequest()
                    .withDates(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1))
                    .withArea(area)
            val exepectedTormaysTarkasteluTulos =
                TormaystarkasteluTulos(
                    autoliikenne =
                        Autoliikenneluokittelu(
                            indeksi = 3.1f,
                            haitanKesto = 1,
                            katuluokka = 4,
                            liikennemaara = 5,
                            kaistahaitta = 2,
                            kaistapituushaitta = 2,
                        ),
                    pyoraliikenneindeksi = 3.0f,
                    linjaautoliikenneindeksi = 3.0f,
                    raitioliikenneindeksi = 5.0f,
                )

            val updatedHakemus = hakemusService.updateHakemus(hakemus.id, request, USERNAME)

            assertThat(updatedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::areas)
                .isNotNull()
                .single()
                .prop(KaivuilmoitusAlue::tyoalueet)
                .isNotNull()
                .single()
                .prop(Tyoalue::tormaystarkasteluTulos)
                .isNotNull()
                .isEqualTo(exepectedTormaysTarkasteluTulos)

            val persistedHakemus = hakemusService.getById(updatedHakemus.id)
            assertThat(persistedHakemus.applicationData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::areas)
                .isNotNull()
                .single()
                .prop(KaivuilmoitusAlue::tyoalueet)
                .isNotNull()
                .single()
                .prop(Tyoalue::tormaystarkasteluTulos)
                .isNotNull()
                .isEqualTo(exepectedTormaysTarkasteluTulos)
        }
    }
}
