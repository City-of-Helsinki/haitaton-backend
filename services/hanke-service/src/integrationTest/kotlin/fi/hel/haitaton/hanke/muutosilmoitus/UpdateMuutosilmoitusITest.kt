package fi.hel.haitaton.hanke.muutosilmoitus

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
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withArea
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
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory.Companion.toUpdateRequest
import fi.hel.haitaton.hanke.findByType
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryException
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.hakemus.Hakemusyhteystieto
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteyshenkiloException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteystietoException
import fi.hel.haitaton.hanke.hakemus.InvalidHiddenRegistryKey
import fi.hel.haitaton.hanke.hakemus.JohtoselvityshakemusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusData
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.Laskutusyhteystieto
import fi.hel.haitaton.hanke.hakemus.Tyoalue
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.PermissionService
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
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired

class UpdateMuutosilmoitusITest(
    @Autowired private val muutosilmoitusService: MuutosilmoitusService,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
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

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `throws exception when the muutosilmoitus does not exist`(type: ApplicationType) {
        val request = HakemusUpdateRequestFactory.createFilledUpdateRequest(type)
        val muutosilmoitusId = UUID.fromString("21404863-edc8-4c28-8d14-35ffc06c04eb")

        val exception = assertFailure {
            muutosilmoitusService.update(muutosilmoitusId, request, USERNAME)
        }

        exception.all {
            hasClass(MuutosilmoitusNotFoundException::class)
            messageContains("id=21404863-edc8-4c28-8d14-35ffc06c04eb")
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `throws exception when the muutosilmoitus has been sent already`(type: ApplicationType) {
        val muutosilmoitus =
            muutosilmoitusFactory
                .builder(type)
                .withSent(DateFactory.getStartDatetime().toOffsetDateTime())
                .save()
        val request = muutosilmoitus.toUpdateRequest()

        val exception = assertFailure {
            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
        }

        exception.all {
            hasClass(MuutosilmoitusAlreadySentException::class)
            messageContains("Muutosilmoitus is already sent to Allu")
            messageContains(
                "Muutosilmoitus: (id=${muutosilmoitus.id}, hakemusId=${muutosilmoitus.hakemusId})"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `throws exception when the request has different type than the saved muutosilmoitus`(
        type: ApplicationType
    ) {
        val muutosilmoitus = muutosilmoitusFactory.builder(type).save()
        val otherType = ApplicationType.entries.first { it != type }
        val request = HakemusUpdateRequestFactory.createFilledUpdateRequest(otherType)

        val exception = assertFailure {
            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
        }

        exception.all {
            hasClass(IncompatibleMuutosilmoitusUpdateException::class)
            messageContains("Invalid update request for muutosilmoitus")
            messageContains("existing type=$type")
            messageContains("requested type=$otherType")
            messageContains(
                "Muutosilmoitus: (id=${muutosilmoitus.id}, hakemusId=${muutosilmoitus.hakemusId})"
            )
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `does not create a new audit log entry when the muutosilmoitus has not changed`(
        type: ApplicationType
    ) {
        val muutosilmoitus = muutosilmoitusFactory.builder(type).save()
        val request = muutosilmoitus.toUpdateRequest()
        auditLogRepository.deleteAll()

        muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

        assertThat(auditLogRepository.findByType(ObjectType.MUUTOSILMOITUS)).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `throws exception when the request has a persisted contact but the muutosilmoitus does not`(
        type: ApplicationType
    ) {
        val muutosilmoitus = muutosilmoitusFactory.builder(type).withoutYhteystiedot().save()
        val requestYhteystietoId = UUID.randomUUID()
        val request =
            muutosilmoitus
                .toUpdateRequest()
                .withCustomerWithContactsRequest(CustomerType.COMPANY, requestYhteystietoId)

        val exception = assertFailure {
            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
        }

        exception.all {
            hasClass(InvalidHakemusyhteystietoException::class)
            messageContains("Invalid hakemusyhteystieto received when updating hakemus")
            messageContains("yhteystietoId=null")
            messageContains("newId=$requestYhteystietoId")
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `throws exception when the request has different persisted contact than the application`(
        type: ApplicationType
    ) {
        val hakemus = hakemusFactory.builder(type).hakija().saveEntity()
        val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
        val originalYhteystietoId = muutosilmoitus.hakemusData.yhteystiedot().single().id
        val requestYhteystietoId = UUID.fromString("ad2173c5-eda4-43e2-8457-7974d74319e8")
        val request =
            muutosilmoitus
                .toUpdateRequest()
                .withCustomer(CustomerType.COMPANY, requestYhteystietoId)

        val exception = assertFailure {
            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
        }

        exception.all {
            hasClass(InvalidHakemusyhteystietoException::class)
            messageContains("Invalid hakemusyhteystieto received when updating hakemus")
            messageContains("yhteystietoId=$originalYhteystietoId")
            messageContains("newId=$requestYhteystietoId")
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `throws exception when the request has a contact that is not a user on hanke`(
        type: ApplicationType
    ) {
        val hakemus = hakemusFactory.builder(type).hakija().saveEntity()
        val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
        val originalYhteystietoId = muutosilmoitus.hakemusData.yhteystiedot().single().id
        val requestHankekayttajaId = UUID.fromString("598e1383-0720-4fd4-8449-21fd759aa457")
        val request =
            muutosilmoitus
                .toUpdateRequest()
                .withCustomer(CustomerType.COMPANY, originalYhteystietoId, requestHankekayttajaId)

        val exception = assertFailure {
            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
        }

        exception.all {
            hasClass(InvalidHakemusyhteyshenkiloException::class)
            messageContains("Invalid hanke user/users received when updating hakemus")
            messageContains("invalidHankeKayttajaIds=[$requestHankekayttajaId]")
        }
    }

    @ParameterizedTest
    @EnumSource(ApplicationType::class)
    fun `doesn't send email when the caller adds themself as contact`(type: ApplicationType) {
        val muutosilmoitus = muutosilmoitusFactory.builder(type).withAreas(listOf()).save()
        val hankeId = hakemusRepository.getReferenceById(muutosilmoitus.hakemusId).hanke.id
        val permission = permissionService.findPermission(hankeId, USERNAME)!!
        val founder = hankekayttajaRepository.findByPermissionId(permission.id)!!
        val request =
            muutosilmoitus
                .toUpdateRequest()
                .withCustomer(CustomerType.COMPANY, null, founder.id)
                .withContractor(CustomerType.COMPANY, null, founder.id)

        muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

        assertThat(greenMail.receivedMessages).isEmpty()
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
        fun `throws exception when there are invalid geometry in areas`() {
            val muutosilmoitus = muutosilmoitusFactory.builder(ApplicationType.CABLE_REPORT).save()
            val request = muutosilmoitus.toUpdateRequest().withArea(intersectingArea)

            val exception = assertFailure {
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
            }

            exception.all {
                hasClass(HakemusGeometryException::class)
                messageContains("Invalid geometry received when updating hakemus")
                messageContains("reason=Self-intersection")
                messageContains(
                    "location={\"type\":\"Point\",\"coordinates\":[25494009.65639264,6679886.142116806]}"
                )
            }
        }

        @Test
        fun `throws exception when area is not inside hanke area`() {
            val muutosilmoitus = muutosilmoitusFactory.builder(ApplicationType.CABLE_REPORT).save()
            val request = muutosilmoitus.toUpdateRequest().withArea(notInHankeArea)

            val exception = assertFailure {
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
            }

            exception.all {
                hasClass(HakemusGeometryNotInsideHankeException::class)
                messageContains("Hakemus geometry doesn't match any hankealue")
                messageContains("geometry=${notInHankeArea.geometry.toJsonString()}")
            }
        }

        @Test
        fun `saves updated data and creates an audit log`() {
            val hakemusEntity =
                hakemusFactory
                    .builder(ApplicationType.CABLE_REPORT)
                    .hakija()
                    .withWorkDescription("Old work description")
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val yhteystieto = muutosilmoitus.hakemusData.yhteystiedot().single()
            val perustajaId = yhteystieto.yhteyshenkilot.single().hankekayttajaId
            val hanke = hankeRepository.findAll().single()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val newDescription = "New work description"
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, yhteystieto.id, perustajaId, newKayttaja.id)
                    .withWorkDescription(newDescription)
            auditLogRepository.deleteAll()

            val updatedMuutosilmoitus =
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(updatedMuutosilmoitus.hakemusData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .all {
                    prop(JohtoselvityshakemusData::workDescription).isEqualTo(newDescription)
                    prop(JohtoselvityshakemusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(perustajaId, newKayttaja.id)
                }
            val applicationLogs = auditLogRepository.findByType(ObjectType.MUUTOSILMOITUS)
            assertThat(applicationLogs).hasSize(1)

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .all {
                    prop(JohtoselvityshakemusData::workDescription).isEqualTo(newDescription)
                    prop(JohtoselvityshakemusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(perustajaId, newKayttaja.id)
                }
        }

        @Test
        fun `removes existing yhteyshenkilot from an yhteystieto`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().saveEntity()
            val kayttaja1 = hankeKayttajaFactory.saveUser(hanke.id)
            val kayttaja2 = hankeKayttajaFactory.saveUser(hanke.id, sahkoposti = "other@email")
            val hakemusEntity =
                hakemusFactory
                    .builder(ApplicationType.CABLE_REPORT)
                    .hakija()
                    .tyonSuorittaja(kayttaja1, kayttaja2)
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val tyonSuorittajaId =
                muutosilmoitus.hakemusData.yhteystiedot().first { it.rooli == TYON_SUORITTAJA }.id
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withContractor(
                        CustomerType.COMPANY,
                        tyonSuorittajaId,
                        hankekayttajaIds = arrayOf(),
                    )

            val updatedMuutosilmoitus =
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(updatedMuutosilmoitus.hakemusData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::contractorWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .isEmpty()

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::contractorWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .isEmpty()
        }

        @Test
        fun `adds a new yhteystieto and an yhteyshenkilo for it at the same time`() {
            val hanke = hankeFactory.builder().withHankealue().saveEntity()
            val hakemusEntity =
                hakemusFactory.builder(USERNAME, hanke, ApplicationType.CABLE_REPORT).saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            assertThat(muutosilmoitus.hakemusData.customerWithContacts).isNull()
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.COMPANY,
                        yhteystietoId = null,
                        hankekayttajaIds = arrayOf(newKayttaja.id),
                    )

            val updatedMuutosilmoitus =
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(updatedMuutosilmoitus.hakemusData)
                .isInstanceOf(JohtoselvityshakemusData::class)
                .prop(JohtoselvityshakemusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { it.hankekayttajaId }
                .containsExactly(newKayttaja.id)

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
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
            val hakemus =
                hakemusFactory.builder(hanke, ApplicationType.CABLE_REPORT).hakija().saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val yhteystietoId = muutosilmoitus.hakemusData.yhteystiedot().single().id
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, yhteystietoId, newKayttaja.id)
                    .withContractor(CustomerType.COMPANY, null, newKayttaja.id)

            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients.single().toString()).isEqualTo(newKayttaja.sahkoposti)
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
        fun `doesn't change project name when application name is changed`() {
            val hakemus =
                hakemusFactory.builderWithGeneratedHanke(nimi = "An application").saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val request = muutosilmoitus.toUpdateRequest().withName("New name")

            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(hankeRepository.findAll().single()).all {
                prop(HankeEntity::nimi).isEqualTo("An application")
            }
        }
    }

    @Nested
    inner class WithKaivuilmoitus {

        private val notInHankeArea =
            ApplicationFactory.createExcavationNotificationArea(
                name = "area",
                tyoalueet = listOf(ApplicationFactory.createTyoalue(GeometriaFactory.polygon())),
            )

        @Test
        fun `throws exception when area is not inside hanke area`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.findAll().single()
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val hankealueId = hanke.alueet.single().id!!
            val area = notInHankeArea.copy(hankealueId = hankealueId)
            val request = muutosilmoitus.toUpdateRequest().withArea(area)

            val exception = assertFailure {
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
            }

            exception.all {
                hasClass(HakemusGeometryNotInsideHankeException::class)
                messageContains("Hakemus geometry is outside the associated hankealue")
                messageContains(
                    "geometry=${notInHankeArea.tyoalueet.single().geometry.toJsonString()}"
                )
            }
        }

        @Test
        fun `throws exception when area references non-existent hankealue`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.findAll().single()
            val hakemus =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val hankealueId = hanke.alueet.single().id!! + 1000
            val area =
                ApplicationFactory.createExcavationNotificationArea(hankealueId = hankealueId)
            val request = muutosilmoitus.toUpdateRequest().withArea(area)

            val exception = assertFailure {
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
            }

            exception.all {
                hasClass(HakemusGeometryNotInsideHankeException::class)
                messageContains("Hakemus geometry is outside the associated hankealue")
                messageContains("hankealue=$hankealueId")
                messageContains("geometry=${area.tyoalueet.single().geometry.toJsonString()}")
            }
        }

        @Test
        fun `saves updated data and creates an audit log`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.findAll().single()
            val hakemusEntity =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .hakija()
                    .withWorkDescription("Old work description")
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val yhteystieto = muutosilmoitus.hakemusData.yhteystiedot().single()
            val kayttajaId = yhteystieto.yhteyshenkilot.single().hankekayttajaId
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!
                )
            val newDescription = "New work description"
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomerWithContactsRequest(
                        CustomerType.COMPANY,
                        yhteystieto.id,
                        kayttajaId,
                        newKayttaja.id,
                    )
                    .withWorkDescription(newDescription)
                    .withRequiredCompetence(true)
                    .withDates(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1))
                    .withArea(area)
            auditLogRepository.deleteAll()

            val updatedMuutosilmoitus =
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(updatedMuutosilmoitus.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .all {
                    prop(KaivuilmoitusData::workDescription).isEqualTo(newDescription)
                    prop(KaivuilmoitusData::requiredCompetence).isTrue()
                    prop(KaivuilmoitusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(kayttajaId, newKayttaja.id)
                    prop(KaivuilmoitusData::areas)
                        .isNotNull()
                        .single()
                        .transform { it.withoutTormaystarkastelut() }
                        .isEqualTo(area.withoutTormaystarkastelut())
                }

            assertThat(auditLogRepository.findByType(ObjectType.MUUTOSILMOITUS)).hasSize(1)

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .all {
                    prop(KaivuilmoitusData::workDescription).isEqualTo(newDescription)
                    prop(KaivuilmoitusData::requiredCompetence).isTrue()
                    prop(KaivuilmoitusData::customerWithContacts)
                        .isNotNull()
                        .prop(Hakemusyhteystieto::yhteyshenkilot)
                        .extracting { it.hankekayttajaId }
                        .containsExactlyInAnyOrder(kayttajaId, newKayttaja.id)
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
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val tyonSuorittajaId =
                muutosilmoitus.hakemusData.yhteystiedot().first { it.rooli == TYON_SUORITTAJA }.id
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withContractor(
                        CustomerType.COMPANY,
                        tyonSuorittajaId,
                        hankekayttajaIds = arrayOf(),
                    )

            val updatedMuutosilmoitus =
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(updatedMuutosilmoitus.hakemusData)
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
            val hakemusEntity =
                hakemusFactory
                    .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            assertThat(muutosilmoitus.hakemusData.customerWithContacts).isNull()
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.COMPANY,
                        yhteystietoId = null,
                        hankekayttajaIds = arrayOf(newKayttaja.id),
                    )

            val updatedMuutosilmoitus =
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(updatedMuutosilmoitus.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::yhteyshenkilot)
                .extracting { it.hankekayttajaId }
                .containsExactly(newKayttaja.id)

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
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
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val yhteystietoId = muutosilmoitus.hakemusData.customerWithContacts!!.id
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.PERSON,
                        yhteystietoId,
                        registryKey = null,
                        registryKeyHidden = true,
                    )

            val failure = assertFailure {
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
            }

            failure.all {
                hasClass(InvalidHiddenRegistryKey::class)
                messageContains("RegistryKeyHidden used in an incompatible way")
                messageContains("New customer type doesn't match the old")
                messageContains("New=PERSON")
                messageContains("Old=COMPANY")
            }
        }

        @ParameterizedTest
        @ValueSource(strings = ["Something completely different"])
        @NullSource
        fun `keeps old customer registry key when registry key hidden is true`(
            newRegistryKey: String?
        ) {
            val henkilotunnus = "090626-885W"
            val hakemusEntity =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .hakija(
                        HakemusyhteystietoFactory.create(
                            tyyppi = CustomerType.PERSON,
                            registryKey = henkilotunnus,
                        )
                    )
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val yhteystietoId = muutosilmoitus.hakemusData.customerWithContacts!!.id
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(
                        CustomerType.PERSON,
                        yhteystietoId,
                        registryKey = newRegistryKey,
                        registryKeyHidden = true,
                    )

            val result = muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(result.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::customerWithContacts)
                .isNotNull()
                .prop(Hakemusyhteystieto::registryKey)
                .isEqualTo(henkilotunnus)

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
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
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val request =
                (muutosilmoitus.toUpdateRequest() as KaivuilmoitusUpdateRequest)
                    .withInvoicingCustomer(
                        CustomerType.PERSON,
                        registryKey = null,
                        registryKeyHidden = true,
                    )

            val failure = assertFailure {
                muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)
            }

            failure.all {
                hasClass(InvalidHiddenRegistryKey::class)
                messageContains("RegistryKeyHidden used in an incompatible way")
                messageContains("New invoicing customer type doesn't match the old")
                messageContains("New=PERSON")
                messageContains("Old=COMPANY")
            }
        }

        @ParameterizedTest
        @ValueSource(strings = ["Something completely different"])
        @NullSource
        fun `keeps old invoicing customer registry key when registry key hidden is true`(
            newRegistryKey: String?
        ) {
            val henkilotunnus = ApplicationFactory.DEFAULT_HENKILOTUNNUS
            val hakemusEntity =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withInvoicingCustomer(ApplicationFactory.createPersonInvoicingCustomer())
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val request =
                (muutosilmoitus.toUpdateRequest() as KaivuilmoitusUpdateRequest)
                    .withInvoicingCustomer(
                        CustomerType.PERSON,
                        registryKey = newRegistryKey,
                        registryKeyHidden = true,
                    )

            val result = muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(result.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::invoicingCustomer)
                .isNotNull()
                .prop(Laskutusyhteystieto::registryKey)
                .isEqualTo(henkilotunnus)
            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
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
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemus).save()
            val yhteystietoId = muutosilmoitus.hakemusData.yhteystiedot().single().id
            val newKayttaja = hankeKayttajaFactory.saveUser(hanke.id)
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withCustomer(CustomerType.COMPANY, yhteystietoId, newKayttaja.id)
                    .withContractor(CustomerType.COMPANY, null, newKayttaja.id)

            muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients.single().toString()).isEqualTo(newKayttaja.sahkoposti)
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
        fun `calculates tormaystarkastelu for work areas`() {
            val hanke = hankeFactory.builder(USERNAME).withHankealue().save()
            val hankeEntity = hankeRepository.findAll().single()
            val hakemusEntity =
                hakemusFactory
                    .builder(USERNAME, hankeEntity, ApplicationType.EXCAVATION_NOTIFICATION)
                    .saveEntity()
            val muutosilmoitus = muutosilmoitusFactory.builder(hakemusEntity).save()
            val area =
                ApplicationFactory.createExcavationNotificationArea(
                    hankealueId = hanke.alueet.single().id!!
                )
            val request =
                muutosilmoitus
                    .toUpdateRequest()
                    .withDates(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1))
                    .withArea(area)
            val expectedTormaystarkasteluTulos =
                TormaystarkasteluTulos(
                    autoliikenne =
                        Autoliikenneluokittelu(
                            indeksi = 2.8f,
                            haitanKesto = 1,
                            katuluokka = 4,
                            liikennemaara = 5,
                            kaistahaitta = 1,
                            kaistapituushaitta = 2,
                        ),
                    pyoraliikenneindeksi = 3.0f,
                    linjaautoliikenneindeksi = 3.0f,
                    raitioliikenneindeksi = 5.0f,
                )

            val result = muutosilmoitusService.update(muutosilmoitus.id, request, USERNAME)

            assertThat(result.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::areas)
                .isNotNull()
                .single()
                .prop(KaivuilmoitusAlue::tyoalueet)
                .isNotNull()
                .single()
                .prop(Tyoalue::tormaystarkasteluTulos)
                .isNotNull()
                .isEqualTo(expectedTormaystarkasteluTulos)

            val hakemus = hakemusRepository.findAll().single()
            val persistedMuutosilmoitus = muutosilmoitusService.find(hakemus.id)!!
            assertThat(persistedMuutosilmoitus.hakemusData)
                .isInstanceOf(KaivuilmoitusData::class)
                .prop(KaivuilmoitusData::areas)
                .isNotNull()
                .single()
                .prop(KaivuilmoitusAlue::tyoalueet)
                .isNotNull()
                .single()
                .prop(Tyoalue::tormaystarkasteluTulos)
                .isNotNull()
                .isEqualTo(expectedTormaystarkasteluTulos)
        }
    }
}
