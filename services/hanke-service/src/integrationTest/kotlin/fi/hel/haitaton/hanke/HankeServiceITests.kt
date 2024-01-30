package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.first
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ALLU_USER_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.attachment.azure.Container.HANKE_LIITTEET
import fi.hel.haitaton.hanke.attachment.common.HankeAttachmentRepository
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.Yhteyshenkilo
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withArea
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeAttachmentFactory
import fi.hel.haitaton.hanke.factory.HankeBuilder.Companion.toModifyRequest
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKE_PERUSTAJA
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeYhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.factory.TEPPO_TESTI
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.logging.AuditLogEntryEntity
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.HankeKayttajaNotFoundException
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaInput
import fi.hel.haitaton.hanke.permissions.HankekayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajakutsuRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.test.Asserts.hasSingleGeometryWithCoordinates
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.Asserts.isRecentUTC
import fi.hel.haitaton.hanke.test.Asserts.isRecentZDT
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasId
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasMockedIp
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.hasUserActor
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.isSuccess
import fi.hel.haitaton.hanke.test.AuditLogEntryEntityAsserts.withTarget
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.TestUtils.nextYear
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import jakarta.persistence.EntityManager
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import net.pwall.mustache.Template
import org.geojson.Feature
import org.geojson.LngLatAlt
import org.geojson.Polygon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

private const val NAME_1 = "etu1 suku1"
private const val NAME_2 = "etu2 suku2"
private const val NAME_3 = "etu3 suku3"
private const val NAME_SOMETHING = "Som Et Hing"
private const val USER_NAME = "test7358"

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USER_NAME)
class HankeServiceITests(
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val auditLogRepository: AuditLogRepository,
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val hankeRepository: HankeRepository,
    @Autowired private val hankekayttajaRepository: HankekayttajaRepository,
    @Autowired private val hankeYhteyshenkiloRepository: HankeYhteyshenkiloRepository,
    @Autowired private val kayttajakutsuRepository: KayttajakutsuRepository,
    @Autowired private val hankeAttachmentRepository: HankeAttachmentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val fileClient: MockFileClient,
    @Autowired private val profiiliClient: ProfiiliClient,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeAttachmentFactory: HankeAttachmentFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val cableReportService: CableReportService,
    @Autowired private val entityManager: EntityManager,
) : DatabaseTest() {

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
        confirmVerified(cableReportService)
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
            val hanke = hankeFactory.builder(USER_NAME).save()

            val response = hankeService.loadHankeById(hanke.id)

            assertThat(response).isNotNull().prop(Hanke::id).isEqualTo(hanke.id)
        }

        @Test
        fun `returns yhteystiedot and yhteyshenkilot if they're present`() {
            val entity =
                hankeFactory.builder(USER_NAME).saveWithYhteystiedot {
                    omistaja(kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET)
                    rakennuttaja(kayttooikeustaso = Kayttooikeustaso.KAIKKIEN_MUOKKAUS)
                    toteuttaja(kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI)
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
    }

    @Nested
    inner class CreateHanke {
        @Test
        fun `returns a new domain object with the correct values`() {
            val request: CreateHankeRequest = HankeFactory.createRequest()
            val securityContext = setUpProfiiliMocks()

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
                prop(Hanke::createdAt).isNotNull().isRecentZDT()
                prop(Hanke::createdBy).isNotNull().isEqualTo(USER_NAME)
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
                prop(Hanke::haittaAjanKestoDays).isNull()
                prop(Hanke::tormaystarkasteluTulos).isNull()
            }
        }

        @Test
        fun `saves object to database with the correct values`() {
            val request: CreateHankeRequest = HankeFactory.createRequest()
            val securityContext = setUpProfiiliMocks()

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
                prop(HankeEntity::createdByUserId).isNotNull().isEqualTo(USER_NAME)
                prop(HankeEntity::createdAt).isNotNull().isRecentUTC()
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
            val securityContext = setUpProfiiliMocks()

            val returnedHanke = hankeService.createHanke(request, securityContext)

            // Verify privileges
            PermissionCode.entries.forEach {
                assertk
                    .assertThat(permissionService.hasPermission(returnedHanke.id, USER_NAME, it))
                    .isTrue()
            }
            val hankeKayttajat = hankekayttajaRepository.findAll()
            assertThat(hankeKayttajat).single().all {
                prop(HankekayttajaEntity::hankeId).isEqualTo(returnedHanke.id)
                prop(HankekayttajaEntity::etunimi).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
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
            val securityContext = setUpProfiiliMocks()

            val hanke = hankeService.createHanke(request, securityContext)

            val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
            assertThat(hankeLogs).single().isSuccess(Operation.CREATE) {
                hasUserActor(USER_NAME)
                hasMockedIp()
                withTarget {
                    hasId(hanke.id)
                    prop(AuditLogTarget::type).isEqualTo(ObjectType.HANKE)
                    prop(AuditLogTarget::objectBefore).isNull()
                    prop(AuditLogTarget::objectAfter).given {
                        val expectedObject = expectedNewHankeLogObject(hanke)
                        JSONAssert.assertEquals(expectedObject, it, JSONCompareMode.STRICT_ORDER)
                    }
                }
            }
        }
    }

    @Test
    fun `updateHanke updates hanke status when given full data`() {
        val hanke = hankeFactory.builder(USER_NAME).withHankealue().save()
        assertThat(hanke.status).isEqualTo(HankeStatus.DRAFT)
        hanke.tyomaaKatuosoite = "Testikatu 1 A 1"
        hanke.withYhteystiedot { id = null }
        val request = hanke.toModifyRequest().copy(tyomaaKatuosoite = "Testikatu 1 A 1")

        val returnedHanke2 = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(returnedHanke2.status).isEqualTo(HankeStatus.PUBLIC)
    }

    @Test
    fun `getHankeApplications return applications`() {
        val hanke = initHankeWithHakemus(123)

        val result = hankeService.getHankeApplications(hanke.hankeTunnus)

        val expectedHakemus = applicationRepository.findAll().first().toDomainObject()
        assertThat(result).hasSameElementsAs(listOf(expectedHakemus))
    }

    @Test
    fun `getHankeApplications hanke does not exist throws not found`() {
        val hankeTunnus = "HAI-1234"

        assertFailure { hankeService.getHankeApplications(hankeTunnus) }
            .all {
                hasClass(HankeNotFoundException::class)
                messageContains(hankeTunnus)
            }
    }

    @Test
    fun `getHankeApplications when no hakemukset returns an empty list`() {
        val hankeInitial = hankeFactory.builder(USER_NAME).save()

        val result = hankeService.getHankeApplications(hankeInitial.hankeTunnus)

        assertThat(result).isEmpty()
    }

    @Test
    fun `updateHanke resets feature properties`() {
        val hanke = hankeFactory.builder(USER_NAME).withHankealue().save()
        val request =
            hanke.toModifyRequest().apply {
                this.alueet[0].geometriat?.featureCollection?.features?.forEach {
                    it.properties["something"] = "fishy"
                }
            }

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertFeaturePropertiesIsReset(result, mapOf("hankeTunnus" to result.hankeTunnus))
    }

    @Test
    fun `updateHanke doesn't revert to a draft`() {
        // Setup Hanke (with all mandatory fields):
        val hanke = hankeFactory.builder(USER_NAME).withYhteystiedot().withHankealue().save()
        assertThat(hanke.status).isEqualTo(HankeStatus.PUBLIC)
        val request = hanke.toModifyRequest().copy(tyomaaKatuosoite = "")

        val exception =
            assertThrows<HankeArgumentException> {
                hankeService.updateHanke(hanke.hankeTunnus, request)
            }

        assertThat(exception).hasMessage("A public hanke didn't have all mandatory fields filled.")
    }

    @Test
    fun `updateHanke with yhteystieto with wrong id should throw`() {
        val hanke = hankeFactory.builder(USER_NAME).withYhteystiedot().withHankealue().save()
        val rubbishId = hanke.extractYhteystiedot().mapNotNull { it.id }.max() + 1
        hanke.omistajat[0].id = rubbishId
        val request = hanke.toModifyRequest()

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HankeYhteystietoNotFoundException::class)
            messageContains("HankeYhteystieto not found for Hanke")
            messageContains(hanke.hankeTunnus)
            messageContains(hanke.id.toString())
            messageContains(rubbishId.toString())
        }
    }

    @Test
    fun `updateHanke can add new Yhteystietos to a group that already has one`() {
        // Setup Hanke with one Yhteystieto:
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistaja(1).save()
        val ytid = hanke.omistajat[0].id!!
        hanke.withOmistaja(i = 2, id = null).withRakennuttaja(i = 3, id = null)
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        // Check that all 3 Yhteystietos are there:
        assertThat(result.omistajat).hasSize(2)
        assertThat(result.rakennuttajat).hasSize(1)
        // Check that the first Yhteystieto has not changed, and the two new ones are as expected:
        // (Not checking all fields, just ensuring the code is not accidentally mixing whole
        // entries).
        assertThat(result.omistajat[0].id).isEqualTo(ytid)
        assertThat(result.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(result.omistajat[1].id).isNotEqualTo(ytid)
        assertThat(result.omistajat[1].nimi).isEqualTo(NAME_2)
        assertThat(result.rakennuttajat[0].id).isNotEqualTo(ytid)
        assertThat(result.rakennuttajat[0].id).isNotEqualTo(result.omistajat[1].id)
        assertThat(result.rakennuttajat[0].nimi).isEqualTo(NAME_3)

        // Use loadHanke and check it returns the same data:
        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            prop(Hanke::omistajat).isEqualTo(result.omistajat)
            prop(Hanke::rakennuttajat).isEqualTo(result.rakennuttajat)
            prop(Hanke::omistajat).first().all {
                prop(HankeYhteystieto::createdAt).isEqualTo(hanke.omistajat[0].createdAt)
                prop(HankeYhteystieto::createdBy).isEqualTo(hanke.omistajat[0].createdBy)
                // The original omistajat-entry was not modified, so modifiedXx-fields must not get
                // values:
                prop(HankeYhteystieto::modifiedAt).isNull()
                prop(HankeYhteystieto::modifiedBy).isNull()
            }
        }
    }

    @Test
    fun `updateHanke updates audit fields`() {
        val hanke = hankeFactory.builder(USER_NAME).create()
        assertThat(hanke).all {
            prop(Hanke::version).isEqualTo(0)
            prop(Hanke::createdAt).isRecentZDT()
            prop(Hanke::createdBy).isEqualTo(USER_NAME)
            prop(Hanke::modifiedAt).isNull()
            prop(Hanke::modifiedBy).isNull()
        }
        val request = hanke.toModifyRequest().copy(kuvaus = "New description")

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result).all {
            prop(Hanke::version).isEqualTo(1)
            prop(Hanke::createdAt).isEqualTo(hanke.createdAt)
            prop(Hanke::createdBy).isEqualTo(hanke.createdBy)
            prop(Hanke::modifiedAt).isNotNull().isRecentZDT(Duration.ofMinutes(10))
            prop(Hanke::modifiedBy).isNotNull().isEqualTo(USER_NAME)
        }
        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            prop(Hanke::version).isEqualTo(1)
            prop(Hanke::createdAt).isEqualTo(hanke.createdAt)
            prop(Hanke::createdBy).isEqualTo(hanke.createdBy)
            prop(Hanke::modifiedAt).isNotNull().isRecentZDT(Duration.ofMinutes(10))
            prop(Hanke::modifiedBy).isNotNull().isEqualTo(USER_NAME)
        }
    }

    @Test
    fun `test changing one existing Yhteystieto in a group with two`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistajat(1, 2).save()
        val ytid1 = hanke.omistajat[0].id!!
        val ytid2 = hanke.omistajat[1].id!!
        assertThat(hanke.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(hanke.omistajat[1].nimi).isEqualTo(NAME_2)
        // Change a value:
        hanke.omistajat[1].nimi = NAME_SOMETHING
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        // Check that both entries kept their ids, and the only change is where expected
        assertThat(result.omistajat).hasSize(2)
        assertThat(result.omistajat[0].id).isEqualTo(ytid1)
        assertThat(result.omistajat[0].nimi).isEqualTo(NAME_1)
        // Check that audit modifiedXx-fields got updated:
        assertThat(result.omistajat[1]).all {
            prop(HankeYhteystieto::id).isEqualTo(ytid2)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_SOMETHING)
            prop(HankeYhteystieto::modifiedAt).isNotNull().isRecentZDT(Duration.ofMinutes(10))
            prop(HankeYhteystieto::modifiedBy).isNotNull().isEqualTo(USER_NAME)
        }

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::omistajat).isEqualTo(result.omistajat)
        }
    }

    @Test
    fun `test that missing yhteystieto gets removed from DB`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistajat(1, 2).save()
        val ytid1 = hanke.omistajat[0].id!!
        hanke.omistajat.removeAt(1)
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result.omistajat).single().all {
            prop(HankeYhteystieto::id).isEqualTo(ytid1)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_1)
        }

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::omistajat).isEqualTo(result.omistajat)
        }
    }

    @Test
    fun `test adding identical Yhteystietos in different groups and removing the other`() {
        // Setup Hanke with two identical Yhteystietos in different group:
        val hanke =
            hankeFactory
                .builder(USER_NAME)
                .withGeneratedOmistaja(1)
                .withGeneratedRakennuttaja(1)
                .save()
        val ytid1 = hanke.omistajat[0].id!!
        val ytid2 = hanke.rakennuttajat[0].id!!
        assertThat(ytid1).isNotEqualTo(ytid2)
        val request = hanke.toModifyRequest().copy(rakennuttajat = listOf())

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.rakennuttajat).isEmpty()
        assertThat(result.omistajat).single().all {
            prop(HankeYhteystieto::id).isEqualTo(ytid1)
            prop(HankeYhteystieto::nimi).isEqualTo(NAME_1)
        }

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::rakennuttajat).isEmpty()
            prop(Hanke::omistajat).hasSameElementsAs(result.omistajat)
        }
    }

    @Test
    fun `updateHanke throws an exception when trying to add a non-existing yhteyshenkilo`() {
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistaja(1).save()
        val kayttajaId = UUID.fromString("c4f0e9a1-8308-47f6-9b26-177635e76b89")
        val omistaja =
            hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HankeKayttajaNotFoundException::class)
            messageContains("HankeKayttaja was not found")
            messageContains(kayttajaId.toString())
        }
    }

    @Test
    fun `updateHanke throws an exception when trying to add an yhteyshenkilo from another hanke`() {
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistaja(1).save()
        val otherHanke = hankeFactory.builder(USER_NAME).save()
        val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(otherHanke.id).id
        val omistaja =
            hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val failure = assertFailure { hankeService.updateHanke(hanke.hankeTunnus, request) }

        failure.all {
            hasClass(HankeKayttajaNotFoundException::class)
            messageContains("HankeKayttaja was not found")
            messageContains(kayttajaId.toString())
        }
    }

    @Test
    fun `updateHanke adds an yhteyshenkilo when adding a new yhteystieto`() {
        val hanke = hankeFactory.builder(USER_NAME).save()
        val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hanke.id).id
        val omistaja =
            HankeYhteystietoFactory.createDifferentiated(1)
                .toModifyRequest(id = null)
                .copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .single()
            .isEqualTo(HankeYhteyshenkiloFactory.kake(kayttajaId))
        val yhteyshenkiloIdentifiers = hankeYhteyshenkiloRepository.findIds()
        assertThat(yhteyshenkiloIdentifiers).single().all {
            prop(HankeYhteyshenkiloIdentifiers::kayttajaId).isEqualTo(kayttajaId)
            prop(HankeYhteyshenkiloIdentifiers::yhteystietoId)
                .isEqualTo(result.omistajat.first().id)
        }
    }

    @Test
    fun `updateHanke adds an yhteyshenkilo when adding a hankekayttaja to an existing yhteystieto`() {
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistaja(1).save()
        val kayttajaId = hankeKayttajaFactory.saveIdentifiedUser(hanke.id).id
        val omistaja =
            hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf(kayttajaId))
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistaja))

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .single()
            .isEqualTo(HankeYhteyshenkiloFactory.kake(kayttajaId))
        val yhteyshenkiloIdentifiers = hankeYhteyshenkiloRepository.findIds()
        assertThat(yhteyshenkiloIdentifiers).single().all {
            prop(HankeYhteyshenkiloIdentifiers::kayttajaId).isEqualTo(kayttajaId)
            prop(HankeYhteyshenkiloIdentifiers::yhteystietoId).isEqualTo(omistaja.id)
        }
    }

    @Test
    fun `updateHanke removes an yhteyshenkilo when removing the only one`() {
        val hankeId = hankeFactory.builder(USER_NAME).saveWithYhteystiedot { omistaja() }.id
        val hanke = hankeService.loadHankeById(hankeId)!!
        val omistajaRequest = hanke.omistajat[0].toModifyRequest().copy(yhteyshenkilot = listOf())
        val request = hanke.toModifyRequest().copy(omistajat = listOf(omistajaRequest))

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat).single().prop(HankeYhteystieto::yhteyshenkilot).isEmpty()
        assertThat(hankeYhteyshenkiloRepository.findAll()).isEmpty()
    }

    @Test
    fun `updateHanke adds and removes correct yhteyshenkilot in a complex setting`() {
        lateinit var kayttaja1: HankekayttajaEntity
        lateinit var kayttaja2: HankekayttajaEntity
        lateinit var kayttaja3: HankekayttajaEntity
        val hankeId =
            hankeFactory
                .builder(USER_NAME)
                .saveWithYhteystiedot {
                    kayttaja1 = kayttaja("kayttaja1")
                    kayttaja2 = kayttaja("kayttaja2")
                    kayttaja3 = kayttaja("kayttaja3")
                    omistaja {
                        addYhteyshenkilo(it, kayttaja1)
                        addYhteyshenkilo(it, kayttaja2)
                    }
                    rakennuttaja {}
                    rakennuttaja {}
                    toteuttaja { addYhteyshenkilo(it, kayttaja3) }
                    muuYhteystieto {
                        addYhteyshenkilo(it, kayttaja1)
                        addYhteyshenkilo(it, kayttaja("kayttaja4"))
                    }
                }
                .id
        val hanke = hankeService.loadHankeById(hankeId)!!
        val newEmail = "new kayttaja"
        val newKayttaja = hankeKayttajaFactory.saveIdentifiedUser(hanke.id, sahkoposti = newEmail)
        // Remove kayttaja2 from omistaja and add new kayttaja
        val omistaja =
            hanke.omistajat[0]
                .toModifyRequest()
                .copy(yhteyshenkilot = listOf(kayttaja1.id, newKayttaja.id))
        // Add kayttaja1 and kayttaja2 to first rakennuttaja
        val rakennuttaja1 =
            hanke.rakennuttajat[0]
                .toModifyRequest()
                .copy(yhteyshenkilot = listOf(kayttaja1.id, kayttaja2.id))
        // Leave the other rakennuttaja without yhteyshenkilo
        val rakennuttaja2 = hanke.rakennuttajat[1].toModifyRequest()
        // Leave toteuttaja with kayttaja3
        val toteuttaja = hanke.toteuttajat[0].toModifyRequest()
        // Remove kayttaja1 and kayttaja4 from muu and add new kayttaja
        val muu = hanke.muut[0].toModifyRequest().copy(yhteyshenkilot = listOf(newKayttaja.id))
        val request =
            hanke
                .toModifyRequest()
                .copy(
                    omistajat = listOf(omistaja),
                    rakennuttajat = listOf(rakennuttaja1, rakennuttaja2),
                    toteuttajat = listOf(toteuttaja),
                    muut = listOf(muu)
                )

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result.omistajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .extracting { it.email }
            .containsExactlyInAnyOrder("kayttaja1", newEmail)
        assertThat(result.rakennuttajat)
            .extracting { yhteystieto -> yhteystieto.yhteyshenkilot.map { it.email } }
            .containsExactlyInAnyOrder(listOf<String>(), listOf("kayttaja1", "kayttaja2"))
        assertThat(result.toteuttajat)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .extracting { it.email }
            .containsExactly("kayttaja3")
        assertThat(result.muut)
            .single()
            .prop(HankeYhteystieto::yhteyshenkilot)
            .extracting { it.email }
            .containsExactly(newEmail)

        val yhteyshenkiloIdentifiers = hankeYhteyshenkiloRepository.findIds()
        assertThat(yhteyshenkiloIdentifiers).hasSize(6)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == omistaja.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(kayttaja1.id, newKayttaja.id)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == rakennuttaja1.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(kayttaja1.id, kayttaja2.id)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == rakennuttaja2.id })
            .isEmpty()
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == toteuttaja.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(kayttaja3.id)
        assertThat(yhteyshenkiloIdentifiers.filter { it.yhteystietoId == muu.id })
            .extracting { it.kayttajaId }
            .containsExactlyInAnyOrder(newKayttaja.id)
    }

    @Test
    fun `test that sending the same Yhteystieto twice without id does not create duplicates`() {
        // Old version of the Yhteystieto should get removed, id increases in response,
        // get-operation returns the new one.
        // NOTE: UI is not supposed to do that, but this situation came up during
        // development/testing, so it is a sort of regression test for the logic.
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistaja(1).save()
        val ytid = hanke.omistajat[0].id
        // Tweaking the returned Yhteystieto-object's id back to null, to make it look like new one.
        hanke.omistajat[0].id = null
        val request = hanke.toModifyRequest()

        val result = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(result).isNotSameInstanceAs(hanke)
        assertThat(result.omistajat).hasSize(1)
        assertThat(result.omistajat[0].id).isNotNull().isNotEqualTo(ytid)

        val loadedHanke = hankeService.loadHanke(result.hankeTunnus)
        assertThat(loadedHanke).isNotNull().all {
            isNotSameInstanceAs(hanke)
            isNotSameInstanceAs(result)
            prop(Hanke::omistajat).hasSameElementsAs(result.omistajat)
        }
    }

    @Test
    fun `test personal data logging`() {
        // Create hanke with two yhteystietos, save and check logs. There should be two rows and the
        // objectBefore fields should be null in them.
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke = hankeFactory.builder(USER_NAME).withGeneratedOmistajat(1, 2).save()
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
            hasUserActor(USER_NAME)
            withTarget {
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                prop(AuditLogTarget::objectBefore).isNull()
                prop(AuditLogTarget::objectAfter).isNotNull().contains(NAME_1)
            }
        }
        assertThat(auditLogEvents2).single().isSuccess(Operation.CREATE) {
            hasUserActor(USER_NAME)
            withTarget {
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                prop(AuditLogTarget::objectBefore).isNull()
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
            hasUserActor(USER_NAME)
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
            hasUserActor(USER_NAME)
            withTarget {
                hasId(yhteystietoId2)
                prop(AuditLogTarget::type).isEqualTo(ObjectType.YHTEYSTIETO)
                prop(AuditLogTarget::objectBefore).isNotNull().contains(NAME_SOMETHING)
                prop(AuditLogTarget::objectAfter).isNull()
            }
        }
    }

    @Test
    @Transactional // due to lazy initialized fields being accessed
    fun `test personal data processing restriction`() {
        // Setup Hanke with two Yhteystietos in different groups. The test will only manipulate the
        // rakennuttaja.
        val hanke =
            hankeFactory
                .builder(USER_NAME)
                .withGeneratedOmistaja(1)
                .withGeneratedRakennuttaja(2)
                .save()
        // Logs must have 2 entries (two yhteystietos were created):
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(2)

        // Get the non-owner yhteystieto, and set the processing restriction (i.e. locked) -flag
        // (must be done via entities):
        // Fetching the yhteystieto is a bit clumsy since we don't have separate a
        // YhteystietoRepository.
        var hankeEntity = hankeRepository.findById(hanke.id).get()
        var yhteystietos = hankeEntity.yhteystiedot
        var rakennuttajaEntity =
            yhteystietos.filter { it.contactType == ContactType.RAKENNUTTAJA }[0]
        val rakennuttajaId = rakennuttajaEntity.id.toString()
        rakennuttajaEntity.dataLocked = true
        // Not setting the info field, or adding audit log entry, since the idea is to only test
        // that the locking actually prevents processing.
        // Saving the hanke will save the yhteystieto in it, too:
        hankeRepository.save(hankeEntity)

        // Try to update the yhteystieto. It should fail and add a new log entry.
        val hankeWithLockedYT = hankeService.loadHanke(hanke.hankeTunnus)
        hankeWithLockedYT!!.rakennuttajat[0].nimi = "Muhaha-Evil-Change"

        assertFailure {
                hankeService.updateHanke(hanke.hankeTunnus, hankeWithLockedYT.toModifyRequest())
            }
            .hasClass(HankeYhteystietoProcessingRestrictedException::class.java)
        // The initial create has created two entries to the log, and now the failed update should
        // have added one more.
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(3)
        var auditLogEvents =
            auditLogRepository
                .findByType(ObjectType.YHTEYSTIETO)
                .map { it.message.auditEvent }
                .filter { it.target.id == rakennuttajaId }
        // For the second yhteystieto, there should be one entry for the earlier creation and
        // another for this failed update.
        assertThat(auditLogEvents).hasSize(2)
        assertThat(auditLogEvents[1].operation).isEqualTo(Operation.UPDATE)
        assertThat(auditLogEvents[1].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents[1].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents[1].status).isEqualTo(Status.FAILED)
        assertThat(auditLogEvents[1].failureDescription)
            .isEqualTo("update hanke yhteystieto BLOCKED by data processing restriction")
        assertThat(auditLogEvents[1].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents[1].target.objectBefore).isNotNull().contains(NAME_2)
        assertThat(auditLogEvents[1].target.objectAfter).isNotNull().contains("Muhaha-Evil-Change")

        // Try to delete the yhteystieto. It should fail and add a new log entry.
        hankeWithLockedYT.rakennuttajat[0] = clearYhteystieto(hankeWithLockedYT.rakennuttajat[0])
        assertFailure {
                hankeService.updateHanke(hanke.hankeTunnus, hankeWithLockedYT.toModifyRequest())
            }
            .hasClass(HankeYhteystietoProcessingRestrictedException::class.java)
        // There should be one more entry in the audit log.
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(4)
        auditLogEvents =
            auditLogRepository
                .findByType(ObjectType.YHTEYSTIETO)
                .map { it.message.auditEvent }
                .filter { it.target.id == rakennuttajaId }
        // For the second yhteystieto, there should be one more audit log entry for this failed
        // deletion:
        assertThat(auditLogEvents).hasSize(3)
        assertThat(auditLogEvents[2].operation).isEqualTo(Operation.DELETE)
        assertThat(auditLogEvents[2].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents[2].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents[2].status).isEqualTo(Status.FAILED)
        assertThat(auditLogEvents[2].failureDescription)
            .isEqualTo("delete hanke yhteystieto BLOCKED by data processing restriction")
        assertThat(auditLogEvents[2].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents[2].target.objectBefore).isNotNull().contains(NAME_2)
        assertThat(auditLogEvents[2].target.objectAfter).isNull()

        // Check that both yhteystietos still exist and the values have not gotten changed.
        val returnedHankeAfterBlockedActions = hankeService.loadHanke(hanke.hankeTunnus)
        assertThat(returnedHankeAfterBlockedActions!!.rakennuttajat)
            .single()
            .prop(HankeYhteystieto::nimi)
            .isEqualTo(NAME_2)

        // Unset the processing restriction flag:
        hankeEntity = hankeRepository.findById(hanke.id).get()
        yhteystietos = hankeEntity.yhteystiedot
        rakennuttajaEntity = yhteystietos.filter { it.contactType == ContactType.RAKENNUTTAJA }[0]
        rakennuttajaEntity.dataLocked = false
        hankeRepository.save(hankeEntity)

        // Updating the yhteystieto should now work:
        val hankeWithUnlockedYT = hankeService.loadHanke(hanke.hankeTunnus)
        hankeWithUnlockedYT!!.rakennuttajat[0].nimi = "Hopefully-Not-Evil-Change"
        val finalHanke =
            hankeService.updateHanke(hanke.hankeTunnus, hankeWithUnlockedYT.toModifyRequest())

        // Check that the change went through:
        assertThat(finalHanke.rakennuttajat[0].nimi).isEqualTo("Hopefully-Not-Evil-Change")
        // There should be one more entry in the log.
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(5)
        auditLogEvents =
            auditLogRepository
                .findByType(ObjectType.YHTEYSTIETO)
                .map { it.message.auditEvent }
                .filter { it.target.id == rakennuttajaId }
        // For the second yhteystieto, there should be one more entry in the log:
        assertThat(auditLogEvents).hasSize(4)
    }

    @Test
    fun `test creation of hanke with alueet`() {
        val alkuPvm = DateFactory.getStartDatetime()
        val loppuPvm = DateFactory.getStartDatetime()
        val hankealue =
            HankealueFactory.create(
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = loppuPvm,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin
                        .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.SATUNNAINEN_HAITTA,
                polyHaitta = Polyhaitta.SATUNNAINEN_HAITTA,
                tarinaHaitta = Tarinahaitta.SATUNNAINEN_HAITTA,
            )
        val createdHanke =
            hankeFactory.builder(USER_NAME).withHankealue().withHankealue(hankealue).save()

        assertThat(createdHanke.alueet).hasSize(2)
        val alue = createdHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(alkuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(loppuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(
                VaikutusAutoliikenteenKaistamaariin
                    .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA
            )
        assertThat(alue.kaistaPituusHaitta)
            .isEqualTo(AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA)
        assertThat(alue.meluHaitta).isEqualTo(Meluhaitta.SATUNNAINEN_HAITTA)
        assertThat(alue.polyHaitta).isEqualTo(Polyhaitta.SATUNNAINEN_HAITTA)
        assertThat(alue.tarinaHaitta).isEqualTo(Tarinahaitta.SATUNNAINEN_HAITTA)
        assertThat(alue.geometriat).isNotNull()
    }

    @Nested
    inner class GenerateHankeWithApplication {

        @Test
        fun `generates hanke based on application`() {
            val inputApplication = ApplicationFactory.cableReportWithoutHanke()

            val application =
                hankeService.generateHankeWithApplication(inputApplication, setUpProfiiliMocks())

            assertThat(application.applicationData.name)
                .isEqualTo(inputApplication.applicationData.name)
            val hanke = hankeRepository.findByHankeTunnus(application.hankeTunnus)!!
            assertThat(hanke.generated).isTrue()
            assertThat(hanke.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(hanke.hankeTunnus).isEqualTo(application.hankeTunnus)
            assertThat(hanke.nimi).isEqualTo(application.applicationData.name)
        }

        @Test
        fun `generates hankekayttaja for founder based on application`() {
            val inputApplication = ApplicationFactory.cableReportWithoutHanke()
            val orderer =
                inputApplication.applicationData
                    .customersWithContacts()
                    .flatMap { it.contacts }
                    .find { it.orderer }
            assertThat(orderer).isNotNull().all {
                prop(Contact::firstName).isEqualTo("Teppo")
                prop(Contact::lastName).isEqualTo("Testihenkilö")
                prop(Contact::email).isEqualTo(ApplicationFactory.TEPPO_EMAIL)
                prop(Contact::phone).isEqualTo("04012345678")
            }

            val application =
                hankeService.generateHankeWithApplication(inputApplication, setUpProfiiliMocks())

            val hanke = hankeRepository.findByHankeTunnus(application.hankeTunnus)!!
            val users = hankekayttajaRepository.findByHankeId(hanke.id)
            assertThat(users.first()).all {
                prop(HankekayttajaEntity::id).isNotNull()
                prop(HankekayttajaEntity::sahkoposti).isEqualTo(ApplicationFactory.TEPPO_EMAIL)
                prop(HankekayttajaEntity::puhelin).isEqualTo("04012345678")
                prop(HankekayttajaEntity::etunimi).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
                prop(HankekayttajaEntity::sukunimi).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
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
            val inputApplication =
                ApplicationFactory.cableReportWithoutHanke()
                    .copy(
                        applicationData =
                            ApplicationFactory.createCableReportApplicationData(name = tooLongName)
                    )

            val application =
                hankeService.generateHankeWithApplication(inputApplication, setUpProfiiliMocks())

            val hanke = hankeRepository.findByHankeTunnus(application.hankeTunnus)!!
            assertThat(hanke.nimi).isEqualTo(expectedName)
            assertThat(hanke.generated).isTrue()
            assertThat(hanke.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(hanke.hankeTunnus).isEqualTo(application.hankeTunnus)
        }

        @Test
        fun `creates hankealueet from the application areas`() {
            val inputApplication =
                ApplicationFactory.cableReportWithoutHanke(
                    ApplicationFactory.createCableReportApplicationData(areas = null)
                        .withArea(name = "Area", geometry = GeometriaFactory.secondPolygon)
                )

            val application =
                hankeService.generateHankeWithApplication(inputApplication, setUpProfiiliMocks())

            val hanke = hankeService.loadHanke(application.hankeTunnus)!!
            assertThat(hanke.alueet).single().all {
                prop(SavedHankealue::nimi).isEqualTo("Hankealue 1")
                hasSingleGeometryWithCoordinates(GeometriaFactory.secondPolygon)
            }
        }

        @Test
        fun `rolls back when application service throws an exception`() {
            // Use an intersecting geometry so that ApplicationService will throw an exception
            val inputApplication =
                ApplicationFactory.cableReportWithoutHanke {
                    withArea(
                        "area",
                        "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json"
                            .asJsonResource()
                    )
                }

            assertThrows<ApplicationGeometryException> {
                hankeService.generateHankeWithApplication(inputApplication, setUpProfiiliMocks())
            }

            assertThat(hankeRepository.findAll()).isEmpty()
        }

        @Test
        fun `should throw if no founder can be deduced from application`() {
            val data =
                ApplicationFactory.createCableReportApplicationData(
                    customerWithContacts =
                        ApplicationFactory.createCompanyCustomer().withContacts() // no orderer
                )
            val application = ApplicationFactory.cableReportWithoutHanke(applicationData = data)
            val securityContext: SecurityContext = mockk()

            val exception =
                assertThrows<HankeArgumentException> {
                    hankeService.generateHankeWithApplication(application, securityContext)
                }

            assertThat(exception).hasMessage("Orderer not found.")
        }
    }

    @Test
    fun `updateHanke creates new hankealue`() {
        val alkuPvm = DateFactory.getStartDatetime()
        val loppuPvm = DateFactory.getStartDatetime()
        val createdHanke = hankeFactory.builder(USER_NAME).withHankealue().save()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = loppuPvm,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin
                        .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.PITKAKESTOINEN_TOISTUVA_HAITTA,
                polyHaitta = Polyhaitta.LYHYTAIKAINEN_TOISTUVA_HAITTA,
                tarinaHaitta = Tarinahaitta.SATUNNAINEN_HAITTA,
            )
        createdHanke.alueet.add(hankealue)
        val request = createdHanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(createdHanke.hankeTunnus, request)

        assertThat(updatedHanke.alueet).hasSize(2)
        val alue = updatedHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(alkuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(loppuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(
                VaikutusAutoliikenteenKaistamaariin
                    .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA
            )
        assertThat(alue.kaistaPituusHaitta)
            .isEqualTo(AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA)
        assertThat(alue.meluHaitta).isEqualTo(Meluhaitta.PITKAKESTOINEN_TOISTUVA_HAITTA)
        assertThat(alue.polyHaitta).isEqualTo(Polyhaitta.LYHYTAIKAINEN_TOISTUVA_HAITTA)
        assertThat(alue.tarinaHaitta).isEqualTo(Tarinahaitta.SATUNNAINEN_HAITTA)
        assertThat(alue.geometriat).isNotNull()
    }

    @Test
    fun `updateHanke new hankealue name updates name and keeps data intact`() {
        val createdHanke = hankeFactory.builder(USER_NAME).withHankealue().save()
        val hankealue = createdHanke.alueet[0]
        assertThat(hankealue.nimi).isEqualTo("Hankealue 1")
        val modifiedHanke =
            createdHanke.copy().apply {
                // manually set mutable collections due to a shallow copy.
                this.omistajat = createdHanke.omistajat
                this.rakennuttajat = createdHanke.rakennuttajat
                this.toteuttajat = createdHanke.toteuttajat
                this.tyomaaTyyppi = createdHanke.tyomaaTyyppi
                this.alueet = mutableListOf(hankealue.copy(nimi = "Changed Name"))
            }
        val request = modifiedHanke.toModifyRequest()

        val updateHankeResult = hankeService.updateHanke(modifiedHanke.hankeTunnus, request)

        assertThat(updateHankeResult)
            .transform { it.copy(modifiedAt = null) }
            .isEqualTo(createdHanke.copy(version = 2, modifiedAt = null))
        assertThat(updateHankeResult.alueet).single().all {
            transform { it.copy(geometriat = null) }
                .isEqualTo(hankealue.copy(geometriat = null, nimi = "Changed Name"))
            prop(SavedHankealue::geometriat)
                .isNotNull()
                .prop(Geometriat::featureCollection)
                .isEqualTo(hankealue.geometriat!!.featureCollection)
        }
    }

    @Test
    fun `updateHanke removes hankealue and geometriat`() {
        val alkuPvm = DateFactory.getStartDatetime()
        val loppuPvm = DateFactory.getStartDatetime()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = loppuPvm,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin
                        .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.SATUNNAINEN_HAITTA,
                polyHaitta = Polyhaitta.LYHYTAIKAINEN_TOISTUVA_HAITTA,
                tarinaHaitta = Tarinahaitta.PITKAKESTOINEN_TOISTUVA_HAITTA,
            )
        val hanke = hankeFactory.builder(USER_NAME).withHankealue().withHankealue(hankealue).save()
        assertThat(hanke.alueet).hasSize(2)
        assertThat(hankealueCount()).isEqualTo(2)
        assertThat(geometriatCount()).isEqualTo(2)
        hanke.alueet.removeAt(0)
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        assertThat(updatedHanke.alueet).single().all {
            prop(SavedHankealue::haittaAlkuPvm).isEqualTo(alkuPvm.truncatedTo(ChronoUnit.DAYS))
            prop(SavedHankealue::haittaLoppuPvm).isEqualTo(loppuPvm.truncatedTo(ChronoUnit.DAYS))
            prop(SavedHankealue::kaistaHaitta)
                .isEqualTo(
                    VaikutusAutoliikenteenKaistamaariin
                        .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA
                )
            prop(SavedHankealue::kaistaPituusHaitta)
                .isEqualTo(AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA)
            prop(SavedHankealue::meluHaitta).isEqualTo(Meluhaitta.SATUNNAINEN_HAITTA)
            prop(SavedHankealue::polyHaitta).isEqualTo(Polyhaitta.LYHYTAIKAINEN_TOISTUVA_HAITTA)
            prop(SavedHankealue::tarinaHaitta)
                .isEqualTo(Tarinahaitta.PITKAKESTOINEN_TOISTUVA_HAITTA)
            prop(SavedHankealue::geometriat).isNotNull()
        }
        val hankeFromDb = hankeService.loadHanke(hanke.hankeTunnus)
        assertThat(hankeFromDb?.alueet).isNotNull().hasSize(1)
        assertThat(hankealueCount()).isEqualTo(1)
        assertThat(geometriatCount()).isEqualTo(1)
    }

    @Nested
    @ExtendWith(MockFileClientExtension::class)
    inner class DeleteHanke {
        @Test
        fun `creates audit log entry for deleted hanke`() {
            val hanke = hankeFactory.builder(USER_NAME).withHankealue().save()
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
            assertEquals(TestUtils.mockedIp, event.actor.ipAddress)
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
                JSONCompareMode.NON_EXTENSIBLE
            )
        }

        @Test
        fun `creates audit log entries for deleted yhteystiedot`() {
            val hanke = hankeFactory.builder(userId = USER_NAME).withYhteystiedot().save()
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
                event.transform { it.actor.ipAddress }.isEqualTo(TestUtils.mockedIp)
            }
            val omistajaId = hanke.omistajat[0].id!!
            val omistajaEvent = deleteLogs.findByTargetId(omistajaId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(omistajaId, 1),
                omistajaEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE
            )
            val rakennuttajaId = hanke.rakennuttajat[0].id!!
            val rakennuttajaEvent = deleteLogs.findByTargetId(rakennuttajaId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(rakennuttajaId, 2),
                rakennuttajaEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE
            )
            val toteuttajaId = hanke.toteuttajat[0].id!!
            val toteuttajaEvent = deleteLogs.findByTargetId(toteuttajaId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(toteuttajaId, 3),
                toteuttajaEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE
            )
            val muuId = hanke.muut[0].id!!
            val muuEvent = deleteLogs.findByTargetId(muuId).message.auditEvent
            JSONAssert.assertEquals(
                expectedYhteystietoDeleteLogObject(muuId, 4),
                muuEvent.target.objectBefore,
                JSONCompareMode.NON_EXTENSIBLE
            )
        }

        @Test
        fun `when no hakemus should delete hanke`() {
            val hanke = hankeFactory.builder(USER_NAME).save()

            hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
        }

        @Test
        fun `when hakemus is pending should delete hanke`() {
            val hakemusAlluId = 356
            val hanke = initHankeWithHakemus(hakemusAlluId)
            every { cableReportService.getApplicationInformation(hakemusAlluId) } returns
                AlluFactory.createAlluApplicationResponse(status = ApplicationStatus.PENDING)
            justRun { cableReportService.cancel(hakemusAlluId) }
            every { cableReportService.sendSystemComment(hakemusAlluId, any()) } returns 1324

            hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
            verifySequence {
                cableReportService.getApplicationInformation(hakemusAlluId)
                cableReportService.getApplicationInformation(hakemusAlluId)
                cableReportService.cancel(hakemusAlluId)
                cableReportService.sendSystemComment(hakemusAlluId, ALLU_USER_CANCELLATION_MSG)
            }
        }

        @Test
        fun `when hakemus is not pending should throw`() {
            val hakemusAlluId = 123
            val hanke = initHankeWithHakemus(hakemusAlluId)
            every { cableReportService.getApplicationInformation(hakemusAlluId) } returns
                AlluFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

            assertThrows<HankeAlluConflictException> {
                hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)
            }

            assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNotNull()
            verify { cableReportService.getApplicationInformation(hakemusAlluId) }
        }

        @Test
        fun `when hanke has users should remove users and tokens`() {
            val hanke = hankeFactory.builder(USER_NAME).save()
            for (i in 1..4) {
                hankeKayttajaFactory.saveUnidentifiedUser(
                    hanke.id,
                    sahkoposti = "email$i@thing.test",
                )
            }
            assertThat(hankekayttajaRepository.findAll()).hasSize(5)
            assertThat(kayttajakutsuRepository.findAll()).hasSize(4)

            hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)

            assertThat(hankeRepository.findAll()).isEmpty()
            assertThat(hankekayttajaRepository.findAll()).isEmpty()
            assertThat(kayttajakutsuRepository.findAll()).isEmpty()
        }

        @Test
        fun `deletes attachments and their contents`() {
            val hanke = hankeFactory.builder(USER_NAME).saveEntity()
            hankeAttachmentFactory.save(hanke = hanke).withContent()
            hankeAttachmentFactory.save(hanke = hanke).withContent()

            hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)

            assertThat(hankeAttachmentRepository.findAll()).isEmpty()
            assertThat(fileClient.listBlobs(HANKE_LIITTEET)).isEmpty()
        }
    }

    @Test
    fun `updateHanke creates audit log entry for updated hanke`() {
        val hanke =
            hankeFactory
                .builder(USER_NAME)
                .withTyomaaKatuosoite("Testikatu 1")
                .withTyomaaTyypit(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
                .save()
        val geometria = GeometriaFactory.create().apply { id = 67 }
        hanke.alueet.add(HankealueFactory.create(id = null, geometriat = geometria))
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val hankeLog = hankeLogs[0]
        assertFalse(hankeLog.isSent)
        assertThat(hankeLog.createdAt).isRecent()
        val event = hankeLog.message.auditEvent
        assertThat(event.dateTime).isRecent()
        assertEquals(Operation.UPDATE, event.operation)
        assertEquals(Status.SUCCESS, event.status)
        assertNull(event.failureDescription)
        assertEquals("1", event.appVersion)
        assertEquals("test7358", event.actor.userId)
        assertEquals(UserRole.USER, event.actor.role)
        assertEquals(TestUtils.mockedIp, event.actor.ipAddress)
        assertEquals(hanke.id.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        val expectedObjectBefore =
            expectedHankeLogObject(hanke, hankeVersion = 1, alkuPvm = null, loppuPvm = null)
        JSONAssert.assertEquals(
            expectedObjectBefore,
            event.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val expectedObjectAfter =
            expectedHankeLogObject(
                hanke,
                updatedHanke.alueet[0],
                hankeVersion = 2,
                tormaystarkasteluTulos = true,
            )
        JSONAssert.assertEquals(
            expectedObjectAfter,
            event.target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `updateHanke creates audit log entry when geometria is updated in hankealue`() {
        val hanke = hankeFactory.builder(USER_NAME).withHankealue().save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()
        hanke.alueet[0].geometriat?.featureCollection?.features =
            listOf(
                Feature().apply {
                    geometry =
                        Polygon(
                            LngLatAlt(24747856.43, 6562789.70),
                            LngLatAlt(24747855.43, 6562789.70),
                            LngLatAlt(24747855.43, 6562788.70),
                            LngLatAlt(24747856.43, 6562789.70)
                        )
                }
            )
        val request = hanke.toModifyRequest()

        val updatedHanke = hankeService.updateHanke(hanke.hankeTunnus, request)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val event = hankeLogs[0].message.auditEvent
        assertEquals(Operation.UPDATE, event.operation)
        assertEquals(hanke.id.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        val expectedObjectBefore =
            expectedHankeLogObject(
                hanke,
                hanke.alueet[0],
                hankeVersion = 1,
                tormaystarkasteluTulos = true
            )
        JSONAssert.assertEquals(
            expectedObjectBefore,
            event.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val templateData =
            TemplateData(
                updatedHanke.id,
                updatedHanke.hankeTunnus,
                updatedHanke.alueet[0].id,
                updatedHanke.alueet[0].geometriat?.id,
                hankeVersion = 2,
                geometriaVersion = 1,
                tormaystarkasteluTulos = true,
                alueNimi = "$HANKEALUE_DEFAULT_NAME 1",
                alkuPvm = updatedHanke.alkuPvm?.format(DateTimeFormatter.ISO_INSTANT),
                loppuPvm = updatedHanke.loppuPvm?.format(DateTimeFormatter.ISO_INSTANT)
            )

        val expectedHankeObject = expectedHankeWithPolygon.processToString(templateData)
        JSONAssert.assertEquals(
            expectedHankeObject,
            event.target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `updateHanke creates audit log entry even if there are no changes`() {
        val hanke =
            hankeFactory
                .builder(USER_NAME)
                .withTyomaaKatuosoite("Testikatu 1")
                .withTyomaaTyypit(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
                .save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        val request = hanke.toModifyRequest()

        hankeService.updateHanke(hanke.hankeTunnus, request)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        val expectedBefore =
            expectedHankeLogObject(hanke, hankeVersion = 1, alkuPvm = null, loppuPvm = null)
        val expectedAfter =
            expectedHankeLogObject(hanke, hankeVersion = 2, alkuPvm = null, loppuPvm = null)
        assertThat(hankeLogs).single().isSuccess(Operation.UPDATE) {
            withTarget {
                prop(AuditLogTarget::objectBefore).given {
                    JSONAssert.assertEquals(expectedBefore, it, JSONCompareMode.NON_EXTENSIBLE)
                }
                prop(AuditLogTarget::objectAfter).given {
                    JSONAssert.assertEquals(expectedAfter, it, JSONCompareMode.NON_EXTENSIBLE)
                }
            }
        }
    }

    private fun initHankeWithHakemus(alluId: Int): HankeEntity {
        val hanke = hankeFactory.saveMinimal(hankeTunnus = "HAI23-1")
        val application =
            applicationRepository.save(
                ApplicationFactory.createApplicationEntity(
                    hanke = hanke,
                    alluStatus = ApplicationStatus.PENDING,
                    alluid = alluId,
                    userId = USER_NAME
                )
            )
        return hanke.apply { hakemukset = mutableSetOf(application) }
    }

    private fun ApplicationEntity.toDomainObject(): Application =
        with(this) {
            Application(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                applicationType,
                applicationData,
                hanke.hankeTunnus,
            )
        }

    private fun assertFeaturePropertiesIsReset(hanke: Hanke, propertiesWanted: Map<String, Any?>) {
        assertThat(hanke.alueet).isNotEmpty()
        hanke.alueet.forEach { alue ->
            val features = alue.geometriat?.featureCollection?.features
            assertThat(features).isNotNull().isNotEmpty()
            features?.forEach { feature ->
                assertThat(feature.properties).isEqualTo(propertiesWanted)
            }
        }
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
    )

    val expectedHankeWithPolygon =
        Template.parse(
            "/fi/hel/haitaton/hanke/logging/expectedHankeWithPolygon.json.mustache"
                .getResourceAsText()
        )

    private fun expectedNewHankeLogObject(hanke: Hanke): String {
        val templateData =
            mapOf("hankeId" to hanke.id.toString(), "hankeTunnus" to hanke.hankeTunnus)
        return Template.parse(
                "/fi/hel/haitaton/hanke/logging/expectedNewHanke.json.mustache".getResourceAsText()
            )
            .processToString(templateData)
    }

    private fun expectedHankeLogObject(
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
            )
        return Template.parse(
                "/fi/hel/haitaton/hanke/logging/expectedHankeWithPoints.json.mustache"
                    .getResourceAsText()
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
     * Clear all information fields from the yhteystieto. Returns a copy.
     *
     * The fields are set to empty strings instead of nulls, since nulls are interpreted as "no
     * change" in update operations.
     *
     * Follows [fi.hel.haitaton.hanke.domain.Yhteystieto.isAnyFieldSet] in which fields are emptied.
     */
    private fun clearYhteystieto(yhteystieto: HankeYhteystieto) =
        yhteystieto.copy(
            nimi = "",
            email = "",
            puhelinnumero = "",
            organisaatioNimi = "",
            osasto = "",
            rooli = "",
            ytunnus = "",
        )

    /**
     * Find all audit logs for a specific object type. Getting all and filtering would obviously not
     * be acceptable in production, but in tests we usually have a very limited number of entities
     * at any one test.
     *
     * This way we don't have to add a new repository method only used in tests.
     */
    fun AuditLogRepository.findByType(type: ObjectType) =
        this.findAll().filter { it.message.auditEvent.target.type == type }

    fun AuditLogRepository.countByType(type: ObjectType) = this.findByType(type).count()

    private fun geometriatCount(): Int? =
        jdbcTemplate.queryForObject("SELECT count(*) from geometriat", Int::class.java)

    private fun hankealueCount(): Int? =
        jdbcTemplate.queryForObject("SELECT count(*) from hankealue", Int::class.java)

    private fun setUpProfiiliMocks(): SecurityContext {
        val securityContext: SecurityContext = mockk()
        every { securityContext.userId() } returns USER_NAME
        every { profiiliClient.getVerifiedName(any()) } returns ProfiiliFactory.DEFAULT_NAMES
        return securityContext
    }

    private fun assertk.Assert<HankeYhteystieto>.hasDefaultInfo() {
        prop(HankeYhteystieto::nimi).isEqualTo(TEPPO_TESTI)
        prop(HankeYhteystieto::email).isEqualTo(ApplicationFactory.TEPPO_EMAIL)
        prop(HankeYhteystieto::tyyppi).isEqualTo(YhteystietoTyyppi.YRITYS)
        prop(HankeYhteystieto::ytunnus).isEqualTo(HankeYhteystietoFactory.DEFAULT_YTUNNUS)
        prop(HankeYhteystieto::puhelinnumero).isEqualTo("04012345678")
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
            prop(Yhteyshenkilo::email).isEqualTo(hankekayttajaInput.email)
            prop(Yhteyshenkilo::puhelinnumero).isEqualTo(hankekayttajaInput.puhelin)
        }
    }
}
