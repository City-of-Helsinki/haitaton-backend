package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.ExpectedHankeLogObject.expectedHankeLogObject
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeBuilder.Companion.toModifyRequest
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKE_PERUSTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_GIVEN_NAME
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_LAST_NAME
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.hakemus.ALLU_USER_CANCELLATION_MSG
import fi.hel.haitaton.hanke.hakemus.Hakemus
import fi.hel.haitaton.hanke.hakemus.HakemusData
import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.logging.AuditLogEntryEntity
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajakutsuRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.Asserts.isRecentUTC
import fi.hel.haitaton.hanke.test.Asserts.isRecentZDT
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasMockedIp
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectAfter
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasNoObjectBefore
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.AuthenticationMocks
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.TestUtils.FIXED_CLOCK
import fi.hel.haitaton.hanke.test.TestUtils.nextYear
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.time.LocalDate
import java.time.OffsetDateTime
import net.pwall.mustache.Template
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

private const val NAME_1 = "etu1 suku1"
private const val NAME_2 = "etu2 suku2"
private const val NAME_SOMETHING = "Som Et Hing"

class HankeServiceITests(
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val kayttajakutsuRepository: KayttajakutsuRepository,
    @Autowired private val hankeAttachmentRepository: HankeAttachmentRepository,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val alluClient: AlluClient,
) : IntegrationTest() {

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
    inner class FindIdentifier {
        val hankeTunnus = "HAI23-13"

        @Test
        fun `Returns null if hanke doesn't exist`() {
            assertThat(hankeService.findIdentifier(hankeTunnus)).isNull()
        }

        @Test
        fun `Returns id and hanke tunnus`() {
            val hanke = hankeFactory.saveMinimal(hankeTunnus = hankeTunnus)

            val result = hankeService.findIdentifier(hankeTunnus)

            assertThat(result).isNotNull().all {
                prop(HankeIdentifier::hankeTunnus).isEqualTo(hankeTunnus)
                prop(HankeIdentifier::id).isNotNull().isEqualTo(hanke.id)
            }
        }
    }

    @Nested
    inner class LoadHankeById {
        @Test
        fun `returns null if hanke not found`() {
            assertThat(hankeService.loadHankeById(44)).isNull()
        }

        @Test
        fun `returns hanke if hanke exists`() {
            val hanke = hankeFactory.builder(USERNAME).save()

            val response = hankeService.loadHankeById(hanke.id)

            assertThat(response).isNotNull().prop(Hanke::id).isEqualTo(hanke.id)
        }

        @Test
        fun `returns hanke deletion date for a completed hanke`() {
            val hanke =
                hankeFactory.builder(USERNAME).saveEntity(HankeStatus.COMPLETED) {
                    it.completedAt = OffsetDateTime.now(FIXED_CLOCK)
                }

            val response = hankeService.loadHankeById(hanke.id)

            assertThat(response)
                .isNotNull()
                .prop(Hanke::deletionDate)
                .isEqualTo(
                    LocalDate.now(FIXED_CLOCK).plusMonthsPreserveEndOfMonth(MONTHS_BEFORE_DELETION)
                )
        }

        @Test
        fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
            val entity =
                hankeFactory.builder(USERNAME).saveWithYhteystiedot {
                    omistaja(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    rakennuttaja(Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    toteuttaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                    muuYhteystieto()
                }

            val hanke = hankeService.loadHankeById(entity.id)!!

            assertThat(hanke.omistajat).single().all {
                hasDefaultInfo()
                hasOneYhteyshenkilo(HankeKayttajaFactory.KAYTTAJA_INPUT_OMISTAJA)
            }
            assertThat(hanke.rakennuttajat).single().all {
                hasDefaultInfo()
                hasOneYhteyshenkilo(HankeKayttajaFactory.KAYTTAJA_INPUT_RAKENNUTTAJA)
            }
            assertThat(hanke.toteuttajat).single().all {
                hasDefaultInfo()
                hasOneYhteyshenkilo(HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA)
            }
            assertThat(hanke.muut).single().all {
                hasDefaultInfo()
                hasOneYhteyshenkilo(HankeKayttajaFactory.KAYTTAJA_INPUT_ASIANHOITAJA)
            }
        }

        private fun assertk.Assert<HankeYhteystieto>.hasDefaultInfo() {
            prop(HankeYhteystieto::nimi).isEqualTo(TEPPO_TESTI)
            prop(HankeYhteystieto::email).isEqualTo(ApplicationFactory.TEPPO_EMAIL)
            prop(HankeYhteystieto::tyyppi).isEqualTo(YhteystietoTyyppi.YRITYS)
            prop(HankeYhteystieto::ytunnus).isEqualTo(HankeYhteystietoFactory.DEFAULT_YTUNNUS)
            prop(HankeYhteystieto::puhelinnumero).isEqualTo(ApplicationFactory.TEPPO_PHONE)
            prop(HankeYhteystieto::organisaatioNimi).isEqualTo("Organisaatio")
            prop(HankeYhteystieto::osasto).isEqualTo("Osasto")
            prop(HankeYhteystieto::createdBy).isEqualTo("test7358")
            prop(HankeYhteystieto::createdAt).isRecentZDT()
            prop(HankeYhteystieto::modifiedBy).isNull()
            prop(HankeYhteystieto::modifiedAt).isNull()
            prop(HankeYhteystieto::rooli).isEqualTo("Isännöitsijä")
        }

        private fun assertk.Assert<HankeYhteystieto>.hasOneYhteyshenkilo(
            hankekayttajaInput: HankekayttajaInput
        ) {
            prop(HankeYhteystieto::yhteyshenkilot).single().all {
                prop(Yhteyshenkilo::id).isNotNull()
                prop(Yhteyshenkilo::etunimi).isEqualTo(hankekayttajaInput.etunimi)
                prop(Yhteyshenkilo::sukunimi).isEqualTo(hankekayttajaInput.sukunimi)
                prop(Yhteyshenkilo::sahkoposti).isEqualTo(hankekayttajaInput.email)
                prop(Yhteyshenkilo::puhelinnumero).isEqualTo(hankekayttajaInput.puhelin)
            }
        }
    }

    @Nested
    inner class CreateHanke {
        @Test
        fun `returns a new domain object with the correct values`() {
            val request: CreateHankeRequest = HankeFactory.createRequest()
            val securityContext = AuthenticationMocks.adLoginMock()

            // Call create and get the return object:
            val returnedHanke = hankeService.createHanke(request, securityContext)

            assertThat(returnedHanke).all {
                // Check the ID is reassigned by the DB:
                prop(Hanke::id).isNotEqualTo(0)
                prop(Hanke::onYKTHanke).isNull()
                prop(Hanke::nimi).isEqualTo(HankeFactory.defaultNimi)
                prop(Hanke::kuvaus).isNull()
                prop(Hanke::vaihe).isNull()
                prop(Hanke::version).isEqualTo(0)
                prop(Hanke::createdAt).isRecentZDT()
                prop(Hanke::createdBy).isNotNull().isEqualTo(USERNAME)
                prop(Hanke::modifiedAt).isNull()
                prop(Hanke::modifiedBy).isNull()
                prop(Hanke::status).isEqualTo(HankeStatus.DRAFT)
                prop(Hanke::generated).isFalse()
                prop(Hanke::vaihe).isNull()
                prop(Hanke::omistajat).isEmpty()
                prop(Hanke::rakennuttajat).isEmpty()
                prop(Hanke::toteuttajat).isEmpty()
                prop(Hanke::muut).isEmpty()
                prop(Hanke::tyomaaKatuosoite).isNull()
                prop(Hanke::tyomaaTyyppi).isEmpty()
                prop(Hanke::alkuPvm).isNull()
                prop(Hanke::loppuPvm).isNull()
                prop(Hanke::alueet).isEmpty()
            }
        }

        @Test
        fun `saves object to database with the correct values`() {
            val request: CreateHankeRequest = HankeFactory.createRequest()
            val securityContext = AuthenticationMocks.adLoginMock()

            // Call create and get the return object:
            val returnedHanke = hankeService.createHanke(request, securityContext)

            val savedHanke = hankeRepository.findByHankeTunnus(returnedHanke.hankeTunnus)
            assertThat(savedHanke).isNotNull().all {
                // Check the ID is reassigned by the DB:
                prop(HankeEntity::id).isNotEqualTo(0)
                prop(HankeEntity::status).isEqualTo(HankeStatus.DRAFT)
                prop(HankeEntity::nimi).isEqualTo(HankeFactory.defaultNimi)
                prop(HankeEntity::kuvaus).isNull()
                prop(HankeEntity::vaihe).isNull()
                prop(HankeEntity::onYKTHanke).isNull()
                prop(HankeEntity::version).isEqualTo(0)
                prop(HankeEntity::createdByUserId).isNotNull().isEqualTo(USERNAME)
                prop(HankeEntity::createdAt).isRecentUTC()
                prop(HankeEntity::modifiedByUserId).isNull()
                prop(HankeEntity::modifiedAt).isNull()
                prop(HankeEntity::generated).isFalse()
                prop(HankeEntity::tyomaaKatuosoite).isNull()
                prop(HankeEntity::tyomaaTyyppi).isEmpty()
            }
        }

        @Test
        fun `creates permissions and a user for the founder`() {
            val request: CreateHankeRequest = HankeFactory.createRequest()
            val securityContext = AuthenticationMocks.adLoginMock()

            val returnedHanke = hankeService.createHanke(request, securityContext)

            // Verify privileges
            PermissionCode.entries.forEach {
                assertThat(permissionService.hasPermission(returnedHanke.id, USERNAME, it)).isTrue()
            }
            val hankeKayttajat = hankekayttajaRepository.findAll()
            assertThat(hankeKayttajat).single().all {
                prop(HankekayttajaEntity::hankeId).isEqualTo(returnedHanke.id)
                prop(HankekayttajaEntity::etunimi).isEqualTo(DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(DEFAULT_LAST_NAME)
                prop(HankekayttajaEntity::puhelin).isEqualTo(DEFAULT_HANKE_PERUSTAJA.puhelinnumero)
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(DEFAULT_HANKE_PERUSTAJA.sahkoposti)
                prop(HankekayttajaEntity::kutsuttuEtunimi).isNull()
                prop(HankekayttajaEntity::kutsuttuSukunimi).isNull()
                prop(HankekayttajaEntity::permission).isNotNull()
                prop(HankekayttajaEntity::kayttajakutsu).isNull()
                prop(HankekayttajaEntity::kutsujaId).isNull()
            }
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        @Test
        fun `creates audit log entry for created hanke`() {
            TestUtils.addMockedRequestIp()
            val request = HankeFactory.createRequest()
            val securityContext = AuthenticationMocks.adLoginMock()

            val hanke = hankeService.createHanke(request, securityContext)

            val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
            assertThat(hankeLogs).single().isSuccess(Operation.CREATE) {
                hasUserActor(USERNAME)
                hasMockedIp()
                withTarget {
                    hasId(hanke.id)
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE)
                    hasNoObjectBefore()
                    prop(AuditLogTarget::objectAfter).given {
                        val expectedObject = expectedNewHankeLogObject(hanke)
                        JSONAssert.assertEquals(expectedObject, it, JSONCompareMode.STRICT_ORDER)
                    }
                }
            }
        }
    }

    @Nested
    inner class GetHankeApplications {
        @Test
        fun `returns applications`() {
            val hanke = initHankeWithHakemus(123)

            val result = hankeService.getHankeApplications(hanke.hankeTunnus)

            val expectedHakemus = hakemusRepository.findAll().single().toMetadata()
            assertThat(result).hasSameElementsAs(listOf(expectedHakemus))
        }

        @Test
        fun `throws not found when hanke does not exist`() {
            val hankeTunnus = "HAI-1234"

            assertFailure { hankeService.getHankeApplications(hankeTunnus) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }

        @Test
        fun `returns an empty list when there are no hakemus`() {
            val hankeInitial = hankeFactory.builder(USERNAME).save()

            val result = hankeService.getHankeApplications(hankeInitial.hankeTunnus)

            assertThat(result).isEmpty()
        }
    }

    @Test
    fun `test personal data logging`() {
        // Create hanke with two yhteystietos, save and check logs. There should be two rows and the
        // objectBefore fields should be null in them.
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke = hankeFactory.builder(USERNAME).withGeneratedOmistajat(1, 2).save()
        // Check and record the Yhteystieto ids, and to-be-changed field's value
        assertThat(hanke.omistajat).hasSize(2)
        val yhteystietoId1 = hanke.omistajat[0].id!!
        val yhteystietoId2 = hanke.omistajat[1].id!!
        assertThat(hanke.omistajat[1].nimi).isEqualTo(NAME_2)

        var auditLogEntries = auditLogRepository.findByType(ObjectType.YHTEYSTIETO)
        // The log must have 2 entries since two yhteystietos were created.
        assertThat(auditLogEntries.count()).isEqualTo(2)
        // Check that each yhteystieto has single entry in log:
        val auditLogEvents1 =
            auditLogEntries.filter { it.message.auditEvent.target.id == yhteystietoId1.toString() }
        val auditLogEvents2 =
            auditLogEntries.filter { it.message.auditEvent.target.id == yhteystietoId2.toString() }

        // Check that each yhteytieto has one entry with correct data.
        assertThat(auditLogEvents1).single().isSuccess(Operation.CREATE) {
            hasUserActor(USERNAME)
            withTarget {
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                hasNoObjectBefore()
                prop(AuditLogTarget::objectAfter).isNotNull().contains(NAME_1)
            }
        }
        assertThat(auditLogEvents2).single().isSuccess(Operation.CREATE) {
            hasUserActor(USERNAME)
            withTarget {
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                hasNoObjectBefore()
                prop(AuditLogTarget::objectAfter).isNotNull().contains(NAME_2)
            }
        }

        // Update one yhteystieto. This should create one update row in the log. ObjectBefore and
        // objectAfter fields should exist and have correct values.
        // Change a value:
        hanke.omistajat[1].nimi = NAME_SOMETHING
        // Call update, get the returned object
        val hankeAfterUpdate = hankeService.updateHanke(hanke.hankeTunnus, hanke.toModifyRequest())
        // Check that both entries kept their ids, and the only change is where expected
        assertThat(hankeAfterUpdate.omistajat).hasSize(2)
        assertThat(hankeAfterUpdate.omistajat[0].id).isEqualTo(yhteystietoId1)
        assertThat(hankeAfterUpdate.omistajat[1].id).isEqualTo(yhteystietoId2)
        assertThat(hankeAfterUpdate.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(hankeAfterUpdate.omistajat[1].nimi).isEqualTo(NAME_SOMETHING)

        auditLogEntries = auditLogRepository.findByType(ObjectType.YHTEYSTIETO)
        // Check that only 1 entry was added to log, about the updated yhteystieto.
        assertThat(auditLogEntries).hasSize(3)

        val updateEntry = auditLogEntries.sortedBy { it.createdAt }.last()
        assertThat(updateEntry).isSuccess(Operation.UPDATE) {
            hasUserActor(USERNAME)
            withTarget {
                hasId(yhteystietoId2)
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                prop(AuditLogTarget::objectBefore).isNotNull().contains(NAME_2)
                prop(AuditLogTarget::objectAfter).isNotNull().contains(NAME_SOMETHING)
            }
        }

        // Delete the other yhteystieto. This should create one update in log, with null
        // objectAfter.
        hankeAfterUpdate.omistajat.removeAt(1)
        // Call update, get the returned object:
        val hankeAfterDelete =
            hankeService.updateHanke(hanke.hankeTunnus, hankeAfterUpdate.toModifyRequest())
        // Check that first yhteystieto remains, second one got removed:
        assertThat(hankeAfterDelete.omistajat).single().all {
            prop(HankeYhteystieto::id).isEqualTo(yhteystietoId1)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_1)
        }

        auditLogEntries = auditLogRepository.findByType(ObjectType.YHTEYSTIETO)
        // Check that only 1 entry was added to log, about the deleted yhteystieto.
        assertThat(auditLogEntries).hasSize(4)

        val deleteEntry = auditLogEntries.sortedBy { it.createdAt }.last()
        assertThat(deleteEntry).isSuccess(Operation.DELETE) {
            hasUserActor(USERNAME)
            withTarget {
                hasId(yhteystietoId2)
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                prop(AuditLogTarget::objectBefore).isNotNull().contains(NAME_SOMETHING)
                hasNoObjectAfter()
            }
        }
    }

    @Nested
    inner class GenerateHankeWithJohtoselvityshakemus {
        private val hakemusNimi = "Johtoselvitys for a private property"

        @Test
        fun `saves hanke based on request`() {
            val request = CreateHankeRequest(hakemusNimi, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            assertThat(hankeRepository.findAll()).single().all {
                prop(HankeEntity::generated).isTrue()
                prop(HankeEntity::status).isEqualTo(HankeStatus.DRAFT)
                prop(HankeEntity::nimi).isEqualTo(hakemusNimi)
                prop(HankeEntity::createdByUserId).isEqualTo(USERNAME)
                prop(HankeEntity::createdAt).isRecentUTC()
            }
        }

        @Test
        fun `saves hakemus based on request`() {
            val request = CreateHankeRequest(hakemusNimi, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            assertThat(hakemusRepository.findAll())
                .single()
                .prop(HakemusEntity::hakemusEntityData)
                .prop(HakemusEntityData::name)
                .isEqualTo(hakemusNimi)
        }

        @Test
        fun `returns the saved hakemus`() {
            val request = CreateHankeRequest(hakemusNimi, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            val hakemus =
                hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            assertThat(hakemus).all {
                prop(Hakemus::applicationData).prop(HakemusData::name).isEqualTo(hakemusNimi)
            }
        }

        @Test
        fun `generates hankekayttaja for founder`() {
            val request = CreateHankeRequest(hakemusNimi, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            val hakemus =
                hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            val hanke = hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)!!
            val kayttajat = hankekayttajaRepository.findByHankeId(hanke.id)
            assertThat(kayttajat).single().all {
                prop(HankekayttajaEntity::id).isNotNull()
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(DEFAULT_HANKE_PERUSTAJA.sahkoposti)
                prop(HankekayttajaEntity::puhelin).isEqualTo(DEFAULT_HANKE_PERUSTAJA.puhelinnumero)
                prop(HankekayttajaEntity::etunimi).isEqualTo(DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(DEFAULT_LAST_NAME)
                prop(HankekayttajaEntity::permission)
                    .isNotNull()
                    .prop(PermissionEntity::kayttooikeustaso)
                    .isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                prop(HankekayttajaEntity::kayttajakutsu).isNull() // no token for creator
                prop(HankekayttajaEntity::kutsujaId).isNull() // no inviter for creator
                prop(HankekayttajaEntity::kutsuttuEtunimi).isNull() // no name in invitation
                prop(HankekayttajaEntity::kutsuttuSukunimi).isNull() // no name in invitation
            }
        }

        @Test
        fun `sets hanke name according to limit and saves successfully`() {
            val expectedName = "a".repeat(MAXIMUM_HANKE_NIMI_LENGTH)
            val tooLongName = expectedName + "bbb"
            val request = CreateHankeRequest(tooLongName, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            val hakemus =
                hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            val hanke = hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)!!
            assertThat(hanke.nimi).isEqualTo(expectedName)
        }

        @Test
        fun `sets the hanke phase to RAKENTAMINEN`() {
            val request = CreateHankeRequest(hakemusNimi, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            val hakemus =
                hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            val hanke = hankeRepository.findByHankeTunnus(hakemus.hankeTunnus)!!
            assertThat(hanke.vaihe).isEqualTo(Hankevaihe.RAKENTAMINEN)
        }

        @Test
        fun `writes to audit logs`() {
            val request = CreateHankeRequest(hakemusNimi, DEFAULT_HANKE_PERUSTAJA)
            val securityContext = AuthenticationMocks.adLoginMock()

            hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)

            assertThat(auditLogRepository.findAll())
                .extracting { it.message.auditEvent.target.type }
                .containsExactlyInAnyOrder(
                    ObjectType.HAKEMUS,
                    ObjectType.HANKE,
                    ObjectType.HANKE_KAYTTAJA,
                    ObjectType.PERMISSION,
                )
        }
    }

    @Nested
    inner class DeleteHanke {
        @Test
        fun `creates audit log entry for deleted hanke`() {
            val hanke =
                hankeFactory
                    .builder(USERNAME)
                    .withHankealue(
                        haittojenhallintasuunnitelma =
                            HaittaFactory.createHaittojenhallintasuunnitelma()
                    )
                    .save()
            auditLogRepository.deleteAll()
            assertEquals(0, auditLogRepository.count())
            TestUtils.addMockedRequestIp()

            hankeService.deleteHanke(hanke.hankeTunnus, "testUser")

            val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
            assertEquals(1, hankeLogs.size)
            val hankeLog = hankeLogs[0]
            assertFalse(hankeLog.isSent)
            assertThat(hankeLog.createdAt).isRecent()
            val event = hankeLog.message.auditEvent
            assertThat(event.dateTime).isRecent()
            assertEquals(Operation.DELETE, event.operation)
            assertEquals(Status.SUCCESS, event.status)
            assertNull(event.failureDescription)
            assertEquals("1", event.appVersion)
            assertEquals("testUser", event.actor.userId)
            assertEquals(UserRole.USER, event.actor.role)
            assertEquals(TestUtils.MOCKED_IP, event.actor.ipAddress)
            assertEquals(hanke.id.toString(), event.target.id)
            assertEquals(ObjectType.HANKE, event.target.type)
            assertNull(event.target.objectAfter)
            val expectedObject =
                expectedHankeLogObject(
                    hanke,
                    hanke.alueet[0],
                    hankeVersion = 1,
                    tormaystarkasteluTulos = true,
                )
            JSONAssert.assertEquals(
                expectedObject,
                event.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE,
            )
        }

        @Test
        fun `creates audit log entries for deleted yhteystiedot`() {
            val hanke = hankeFactory.builder(userId = USERNAME).withYhteystiedot().save()
            auditLogRepository.deleteAll()
            assertEquals(0, auditLogRepository.count())
            assertThat(hanke.extractYhteystiedot().size).isEqualTo(4)
            TestUtils.addMockedRequestIp()

            hankeService.deleteHanke(hanke.hankeTunnus, "testUser")

            val logs = auditLogRepository.findByType(ObjectType.YHTEYSTIETO)
            assertEquals(4, logs.size)
            val deleteLogs = logs.filter { it.message.auditEvent.operation == Operation.DELETE }
            assertThat(deleteLogs).hasSize(4)
            assertThat(deleteLogs).each { log ->
                log.transform { it.isSent }.isFalse()
                log.transform { it.createdAt }.isRecent()
                val event = log.transform { it.message.auditEvent }
                event.transform { it.dateTime }.isRecent()
                event.transform { it.status }.isEqualTo(Status.SUCCESS)
                event.transform { it.failureDescription }.isNull()
                event.transform { it.appVersion }.isEqualTo("1")
                event.transform { it.actor.userId }.isEqualTo("testUser")
                event.transform { it.actor.role }.isEqualTo(UserRole.USER)
                event.transform { it.actor.ipAddress }.isEqualTo(TestUtils.MOCKED_IP)
            }
            val omistajaId = hanke.omistajat[0].id!!
            val omistajaEvent = deleteLogs.findByTargetId(omistajaId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(omistajaId, 1),
                omistajaEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            val rakennuttajaId = hanke.rakennuttajat[0].id!!
            val rakennuttajaEvent = deleteLogs.findByTargetId(rakennuttajaId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(rakennuttajaId, 2),
                rakennuttajaEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            val toteuttajaId = hanke.toteuttajat[0].id!!
            val toteuttajaEvent = deleteLogs.findByTargetId(toteuttajaId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(toteuttajaId, 3),
                toteuttajaEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            val muuId = hanke.muut[0].id!!
            val muuEvent = deleteLogs.findByTargetId(muuId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(muuId, 4),
                muuEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE,
            )
        }

        @Test
        fun `when no hakemus should delete hanke`() {
            val hanke = hankeFactory.builder(USERNAME).save()

            hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
        }

        @Test
        fun `when hakemus is pending should delete hanke`() {
            val hakemusAlluId = 356
            val hanke = initHankeWithHakemus(hakemusAlluId)
            every { alluClient.getApplicationInformation(hakemusAlluId) } returns
                AlluFactory.createAlluApplicationResponse(status = ApplicationStatus.PENDING)
            justRun { alluClient.cancel(hakemusAlluId) }
            every { alluClient.sendSystemComment(hakemusAlluId, any()) } returns 1324

            hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
            verifySequence {
                alluClient.getApplicationInformation(hakemusAlluId)
                alluClient.getApplicationInformation(hakemusAlluId)
                alluClient.cancel(hakemusAlluId)
                alluClient.sendSystemComment(hakemusAlluId, ALLU_USER_CANCELLATION_MSG)
            }
        }

        @Test
        fun `when hakemus is cancelled should delete hanke`() {
            val hanke = initHankeWithHakemus(123, ApplicationStatus.CANCELLED)

            hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
        }

        @Test
        fun `when hakemus is not pending or cancelled should throw`() {
            val hakemusAlluId = 123
            val hanke = initHankeWithHakemus(hakemusAlluId)
            every { alluClient.getApplicationInformation(hakemusAlluId) } returns
                AlluFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

            assertThrows<HankeAlluConflictException> {
                hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)
            }

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNotNull()
            verify { alluClient.getApplicationInformation(hakemusAlluId) }
        }

        @Test
        fun `when hakemus has yhteyshenkilot, those are removed as well`() {
            val hanke =
                hankeFactory.builder(USERNAME).withYhteystiedot().withHankealue().saveEntity()
            hakemusFactory.builder(hanke).hakija().save()

            hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
        }

        @Test
        fun `when hanke has users should remove users and tokens`() {
            val hanke = hankeFactory.builder(USERNAME).save()
            for (i in 1..4) {
                hankeKayttajaFactory.saveUnidentifiedUser(
                    hanke.id,
                    sahkoposti = "email$i@thing.test",
                )
            }
            assertThat(hankekayttajaRepository.findAll()).hasSize(5)
            assertThat(kayttajakutsuRepository.findAll()).hasSize(4)

            hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)

            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        @Test
        fun `deletes attachments and their contents`() {
            val hanke = hankeFactory.builder(USERNAME).saveEntity()
            hankeAttachmentFactory.save(hanke = hanke).withContent()
            hankeAttachmentFactory.save(hanke = hanke).withContent()

            hankeService.deleteHanke(hanke.hankeTunnus, USERNAME)

            assertThat(hankeAttachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
        }
    }

    private fun initHankeWithHakemus(
        alluId: Int,
        alluStatus: ApplicationStatus = ApplicationStatus.PENDING,
    ): HankeEntity {
        hakemusFactory.builder().withStatus(alluId = alluId, status = alluStatus).saveEntity()

        return hankeRepository.findAll().first()
    }

    private fun expectedNewHankeLogObject(hanke: Hanke): String {
        val templateData =
            mapOf("hankeId" to hanke.id.toString(), "hankeTunnus" to hanke.hankeTunnus)
        return Template.parse(
                "/fi/hel/haitaton/hanke/logging/expectedNewHanke.json.mustache".getResourceAsText()
            )
            .processToString(templateData)
    }

    private fun List<AuditLogEntryEntity>.findByTargetId(id: Int): AuditLogEntryEntity =
        this.filter { it.message.auditEvent.target.id == id.toString() }[0]

    /**
     * Creates the logged object with the same content as
     * [fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.createDifferentiated].
     */
    private fun expectedYhteystietoDeleteLogObject(id: Int?, i: Int) =
        """{
          "id":$id,
          "nimi":"etu$i suku$i",
          "email":"email$i",
          "ytunnus": ${HankeYhteystietoFactory.DEFAULT_YTUNNUS},
          "puhelinnumero":"010$i$i$i$i$i$i$i",
          "organisaatioNimi":"org$i",
          "osasto":"osasto$i",
          "rooli": "Isännöitsijä$i",
          "tyyppi": "YHTEISO",
          "yhteyshenkilot": []
        }"""

    /**
     * Find all audit logs for a specific object type. Getting all and filtering would obviously not
     * be acceptable in production, but in tests we usually have a very limited number of entities
     * at any one test.
     *
     * This way we don't have to add a new repository method only used in tests.
     */
    fun AuditLogRepository.findByType(type: ObjectType) =
        this.findAll().filter { it.message.auditEvent.target.type == type }
}

object ExpectedHankeLogObject {
    private val expectedHankeWithPolygon =
        Template.parse(
            "/fi/hel/haitaton/hanke/logging/expectedHankeWithPolygon.json.mustache"
                .getResourceAsText()
        )

    fun expectedHankeLogObject(
        hanke: Hanke,
        alue: SavedHankealue? = null,
        geometriaVersion: Int = 0,
        hankeVersion: Int = 0,
        tormaystarkasteluTulos: Boolean = false,
        alkuPvm: String? = "${nextYear()}-02-20T00:00:00Z",
        loppuPvm: String? = "${nextYear()}-02-21T00:00:00Z",
    ): String {
        val templateData =
            TemplateData(
                hanke.id,
                hanke.hankeTunnus,
                alue?.id,
                alue?.geometriat?.id,
                geometriaVersion,
                hankeVersion,
                nextYear(),
                tormaystarkasteluTulos,
                alue?.nimi,
                alkuPvm,
                loppuPvm,
                alue?.haittojenhallintasuunnitelma != null,
            )
        return expectedHankeWithPolygon.processToString(templateData)
    }

    data class TemplateData(
        val hankeId: Int,
        val hankeTunnus: String,
        val alueId: Int? = null,
        val geometriaId: Int? = null,
        val geometriaVersion: Int = 0,
        val hankeVersion: Int = 0,
        val nextYear: Int = nextYear(),
        val tormaystarkasteluTulos: Boolean = false,
        val alueNimi: String? = null,
        val alkuPvm: String? = null,
        val loppuPvm: String? = null,
        val haittojenhallintasuunnitelma: Boolean = false,
    )
}
