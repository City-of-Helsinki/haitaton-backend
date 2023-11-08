package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertions.each
import assertk.assertions.first
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ALLU_USER_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YHTEISO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withArea
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.defaultKuvaus
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.defaultYtunnus
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.logging.AuditLogEntryEntity
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.permissions.HankeKayttajaRepository
import fi.hel.haitaton.hanke.permissions.KayttajaTunnisteRepository
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.Asserts.hasSingleGeometryWithCoordinates
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.TestUtils
import fi.hel.haitaton.hanke.test.TestUtils.nextYear
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import net.pwall.mustache.Template
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.byLessThan
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
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

private const val NAME_1 = "etu1 suku1"
private const val NAME_2 = "etu2 suku2"
private const val NAME_3 = "etu3 suku3"
private const val NAME_4 = "etu4 suku4"
private const val NAME_SOMETHING = "Som Et Hing"
private const val USER_NAME = "test7358"

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USER_NAME)
class HankeServiceITests : DatabaseTest() {

    @MockkBean private lateinit var cableReportService: CableReportService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var applicationRepository: ApplicationRepository
    @Autowired private lateinit var hankeRepository: HankeRepository
    @Autowired private lateinit var hankeKayttajaRepository: HankeKayttajaRepository
    @Autowired private lateinit var kayttajaTunnisteRepository: KayttajaTunnisteRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var hankeFactory: HankeFactory

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

            assertk.assertThat(result).isNotNull().all {
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
            val hanke = hankeFactory.save()

            val response = hankeService.loadHankeById(hanke.id)

            assertk.assertThat(response).isNotNull().prop(Hanke::id).isEqualTo(hanke.id)
        }
    }

    @Test
    fun `create Hanke with full data set succeeds and returns a new domain object with the correct values`() {
        val request: CreateHankeRequest =
            HankeFactory.createRequest(vaihe = Vaihe.SUUNNITTELU)
                .withYhteystiedot()
                .withHankealue()
                .build()

        val datetimeAlku = request.alueet!![0].haittaAlkuPvm // nextyear.2.20 23:45:56Z
        val datetimeLoppu = request.alueet!![0].haittaLoppuPvm // nextyear.2.21 0:12:34Z
        // For checking audit field datetimes (with some minutes of margin for test running delay):
        val currentDatetime = getCurrentTimeUTC()

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(request)

        // Verify privileges
        PermissionCode.entries.forEach {
            assertThat(permissionService.hasPermission(returnedHanke.id, USER_NAME, it)).isTrue()
        }
        // Check the ID is reassigned by the DB:
        assertThat(returnedHanke.id).isNotEqualTo(0)
        // Check the fields:
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = // nextyear.2.20 00:00:00Z
            datetimeAlku!!.truncatedTo(ChronoUnit.DAYS)
        val expectedDateLoppu = // nextyear.2.21 00:00:00Z
            datetimeLoppu!!.truncatedTo(ChronoUnit.DAYS)
        assertThat(returnedHanke.status).isEqualTo(HankeStatus.PUBLIC)
        assertThat(returnedHanke.nimi).isEqualTo("Hämeentien perusparannus ja katuvalot")
        assertThat(returnedHanke.kuvaus).isEqualTo(defaultKuvaus)
        assertThat(returnedHanke.alkuPvm).isEqualTo(expectedDateAlku)
        assertThat(returnedHanke.loppuPvm).isEqualTo(expectedDateLoppu)
        assertThat(returnedHanke.vaihe).isEqualTo(Vaihe.SUUNNITTELU)
        assertThat(returnedHanke.tyomaaKatuosoite).isEqualTo("Testikatu 1")
        assertThat(returnedHanke.tyomaaTyyppi).contains(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        assertThat(returnedHanke.alueet[0].kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI)
        assertThat(returnedHanke.alueet[0].kaistaPituusHaitta)
            .isEqualTo(KaistajarjestelynPituus.NELJA)
        assertThat(returnedHanke.alueet[0].meluHaitta).isEqualTo(Haitta13.YKSI)
        assertThat(returnedHanke.alueet[0].polyHaitta).isEqualTo(Haitta13.KAKSI)
        assertThat(returnedHanke.alueet[0].tarinaHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(returnedHanke.version).isZero
        assertThat(returnedHanke.createdAt).isNotNull
        assertThat(returnedHanke.createdAt!!.toEpochSecond() - currentDatetime.toEpochSecond())
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke.createdBy).isNotNull
        assertThat(returnedHanke.createdBy).isEqualTo(USER_NAME)
        assertThat(returnedHanke.modifiedAt).isNull()
        assertThat(returnedHanke.modifiedBy).isNull()
        val omistaja: HankeYhteystieto = returnedHanke.omistajat[0]
        val rakennuttaja: HankeYhteystieto = returnedHanke.rakennuttajat[0]
        val toteuttaja: HankeYhteystieto = returnedHanke.toteuttajat[0]
        val muu: HankeYhteystieto = returnedHanke.muut[0]
        // Check yhteystieto tyyppi
        listOf(omistaja, rakennuttaja, toteuttaja, muu).forEach {
            assertThat(listOf(YRITYS, YKSITYISHENKILO, YHTEISO)).contains(it.tyyppi)
        }
        // Check that fields have not somehow gone mixed between yhteystietos:
        assertThat(omistaja.nimi).isEqualTo(NAME_1)
        assertThat(rakennuttaja.nimi).isEqualTo(NAME_2)
        assertThat(toteuttaja.nimi).isEqualTo(NAME_3)
        assertThat(muu.nimi).isEqualTo(NAME_4)
        // Check muu specific fields
        assertThat(muu.organisaatioNimi).isNotEmpty
        assertThat(muu.osasto).isNotEmpty
        assertThat(muu.rooli).isNotEmpty
        listOf(omistaja, rakennuttaja, toteuttaja, muu).forEach {
            assertThat(it.ytunnus).isEqualTo(defaultYtunnus)
            verifyYhteyshenkilot(it.alikontaktit)
        }
        // Check that all fields got there and back (with just one of the Yhteystietos):
        assertThat(omistaja.email).isEqualTo("email1")
        assertThat(omistaja.puhelinnumero).isEqualTo("0101111111")
        assertThat(omistaja.organisaatioNimi).isEqualTo("org1")
        assertThat(omistaja.osasto).isEqualTo("osasto1")
        // Check all the fields generated by backend (id, audits):
        // NOTE: can not guarantee a specific id here, but the ids should be different to each other
        assertThat(omistaja.id).isNotNull
        val firstId = omistaja.id!!
        assertThat(omistaja.createdAt).isNotNull
        assertThat(omistaja.createdAt!!.toEpochSecond() - currentDatetime.toEpochSecond())
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(omistaja.createdBy).isNotNull
        assertThat(omistaja.createdBy).isEqualTo(USER_NAME)
        assertThat(omistaja.modifiedAt).isNull()
        assertThat(omistaja.modifiedBy).isNull()
        assertThat(rakennuttaja.id).isNotEqualTo(firstId)
        assertThat(toteuttaja.id).isNotEqualTo(firstId)
        assertThat(toteuttaja.id).isNotEqualTo(rakennuttaja.id)
        assertThat(hankeKayttajaRepository.findAll()).hasSize(4)
        assertThat(kayttajaTunnisteRepository.findAll()).hasSize(4)
    }

    @Test
    fun `create Hanke with without perustaja and contacts does not create hanke users`() {
        val request = HankeFactory.createRequest().build()

        val hanke = hankeService.createHanke(request)

        val hankeEntity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus)!!
        assertThat(hankeEntity.perustaja).isNull()
        assertThat(hankeKayttajaRepository.findAll()).isEmpty()
        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
    }

    @Test
    fun `create Hanke with partial data set and update with full data set give correct status`() {
        // Setup create hanke request (without any yhteystieto) and with at least one null field:
        val request =
            HankeFactory.createRequest()
                .withHankealue()
                .withRequest { copy(tyomaaKatuosoite = null) }
                .build()

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(request)

        // Check status:
        assertThat(returnedHanke.status).isEqualTo(HankeStatus.DRAFT)

        // Fill the values
        returnedHanke.tyomaaKatuosoite = "Testikatu 1 A 1"
        returnedHanke.withYhteystiedot { id = null }

        // Call update
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)

        // Check the return object in general:
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)

        // Check that status changed now with full data available:
        assertThat(returnedHanke2.status).isEqualTo(HankeStatus.PUBLIC)
    }

    @Test
    fun `createHanke resets feature properties`() {
        val alue = HankealueFactory.create(id = null, hankeId = null)
        alue.geometriat?.featureCollection?.features?.forEach {
            it.properties["something"] = "fishy"
        }
        val request = HankeFactory.createRequest().withHankealue(alue).build()

        val hanke = hankeService.createHanke(request)

        assertFeaturePropertiesIsReset(hanke, mutableMapOf("hankeTunnus" to hanke.hankeTunnus))
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
        val hankeInitial = hankeFactory.save()

        val result = hankeService.getHankeApplications(hankeInitial.hankeTunnus)

        assertThat(result).isEmpty()
    }

    @Test
    fun `updateHanke resets feature properties`() {
        val hanke = hankeFactory.createRequest().withHankealue().save()
        hanke.apply {
            this.alueet[0].geometriat?.featureCollection?.features?.forEach {
                it.properties["something"] = "fishy"
            }
        }

        val result = hankeService.updateHanke(hanke)

        assertFeaturePropertiesIsReset(result, mapOf("hankeTunnus" to result.hankeTunnus))
    }

    @Test
    fun `updateHanke ignores the status field in the given hanke`() {
        val hanke = hankeFactory.createRequest().save()
        hanke.status = HankeStatus.PUBLIC

        val returnedHanke = hankeService.updateHanke(hanke)

        assertThat(returnedHanke.status).isEqualTo(HankeStatus.DRAFT)
    }

    @Test
    fun `updateHanke doesn't revert to a draft`() {
        // Setup Hanke (with all mandatory fields):
        val hanke = hankeFactory.createRequest().withYhteystiedot().withHankealue().save()
        assertThat(hanke.status).isEqualTo(HankeStatus.PUBLIC)
        hanke.tyomaaKatuosoite = ""

        val exception = assertThrows<HankeArgumentException> { hankeService.updateHanke(hanke) }

        assertThat(exception).hasMessage("A public hanke didn't have all mandatory fields filled.")
    }

    @Test
    fun `test adding a new Yhteystieto to a group that already has one and to another group`() {
        // Also tests how update affects audit fields.

        // Setup Hanke with one Yhteystieto:
        val request = HankeFactory.createRequest().withGeneratedOmistaja(1).build()

        val returnedHanke = hankeService.createHanke(request)
        // Check and record the Yhteystieto's id
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        val ytid = returnedHanke.omistajat[0].id!!

        returnedHanke
            .withGeneratedOmistaja(2) { id = null }
            .withGeneratedRakennuttaja(3) { id = null }

        // For checking audit field datetimes (with some minutes of margin for test running delay):
        val currentDatetime = getCurrentTimeUTC()

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)

        // A small side-check here for audit and version fields handling on update:
        assertThat(returnedHanke2.version).isEqualTo(1)
        assertThat(returnedHanke2.createdAt).isEqualTo(returnedHanke.createdAt)
        assertThat(returnedHanke2.createdBy).isEqualTo(returnedHanke.createdBy)
        assertThat(returnedHanke2.modifiedAt).isNotNull
        assertThat(returnedHanke2.modifiedAt!!.toEpochSecond() - currentDatetime.toEpochSecond())
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke2.modifiedBy).isNotNull
        assertThat(returnedHanke2.modifiedBy).isEqualTo(USER_NAME)

        // Check that all 3 Yhteystietos are there:
        assertThat(returnedHanke2.omistajat).hasSize(2)
        assertThat(returnedHanke2.rakennuttajat).hasSize(1)
        // Check that the first Yhteystieto has not changed, and the two new ones are as expected:
        // (Not checking all fields, just ensuring the code is not accidentally mixing whole
        // entries).
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(returnedHanke2.omistajat[1].nimi).isEqualTo(NAME_2)
        assertThat(returnedHanke2.rakennuttajat[0].nimi).isEqualTo(NAME_3)
        assertThat(returnedHanke2.omistajat[1].id).isNotEqualTo(ytid)
        assertThat(returnedHanke2.rakennuttajat[0].id).isNotEqualTo(ytid)
        assertThat(returnedHanke2.rakennuttajat[0].id).isNotEqualTo(returnedHanke2.omistajat[1].id)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)

        // Check that the returned hanke has the same 3 Yhteystietos:
        assertThat(returnedHanke3!!.omistajat).hasSize(2)
        assertThat(returnedHanke3.rakennuttajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid)
        assertThat(returnedHanke3.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(returnedHanke3.omistajat[1].nimi).isEqualTo(NAME_2)
        assertThat(returnedHanke3.rakennuttajat[0].nimi).isEqualTo(NAME_3)
        assertThat(returnedHanke3.omistajat[1].id).isNotEqualTo(ytid)
        assertThat(returnedHanke3.rakennuttajat[0].id).isNotEqualTo(ytid)
        assertThat(returnedHanke3.rakennuttajat[0].id).isNotEqualTo(returnedHanke3.omistajat[1].id)

        // A small side-check here for audit fields handling on load:
        assertThat(returnedHanke3.omistajat[0].createdAt)
            .isEqualTo(returnedHanke.omistajat[0].createdAt)
        assertThat(returnedHanke3.omistajat[0].createdBy)
            .isEqualTo(returnedHanke.omistajat[0].createdBy)
        // The original omistajat-entry was not modified, so modifiedXx-fields must not get values:
        assertThat(returnedHanke3.omistajat[0].modifiedAt).isNull()
        assertThat(returnedHanke3.omistajat[0].modifiedBy).isNull()
    }

    @Test
    fun `test changing one existing Yhteystieto in a group with two`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val request = HankeFactory.createRequest().withGeneratedOmistajat(1, 2).build()

        val returnedHanke = hankeService.createHanke(request)
        // Check and record the Yhteystieto ids, and to-be-changed field's value
        assertThat(returnedHanke.omistajat).hasSize(2)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        assertThat(returnedHanke.omistajat[1].id).isNotNull
        val ytid1 = returnedHanke.omistajat[0].id!!
        val ytid2 = returnedHanke.omistajat[1].id!!
        assertThat(returnedHanke.omistajat[1].nimi).isEqualTo(NAME_2)

        // Change a value:
        returnedHanke.omistajat[1].nimi = NAME_SOMETHING

        // For checking audit field datetimes (with some minutes of margin for test running delay):
        val currentDatetime = getCurrentTimeUTC()

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)

        // Check that both entries kept their ids, and the only change is where expected
        assertThat(returnedHanke2.omistajat).hasSize(2)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[1].id).isEqualTo(ytid2)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(returnedHanke2.omistajat[1].nimi).isEqualTo(NAME_SOMETHING)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)

        // Check that the returned hanke has the same 2 Yhteystietos:
        assertThat(returnedHanke3!!.omistajat).hasSize(2)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[1].id).isEqualTo(ytid2)
        assertThat(returnedHanke3.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(returnedHanke3.omistajat[1].nimi).isEqualTo(NAME_SOMETHING)

        // Check that audit modifiedXx-fields got updated:
        assertThat(returnedHanke3.omistajat[1].modifiedAt).isNotNull
        assertThat(
                returnedHanke3.omistajat[1].modifiedAt!!.toEpochSecond() -
                    currentDatetime.toEpochSecond()
            )
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke3.omistajat[1].modifiedBy).isNotNull
        assertThat(returnedHanke3.omistajat[1].modifiedBy).isEqualTo(USER_NAME)
    }

    @Test
    fun `test that existing Yhteystieto that is sent fully empty gets removed`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val request = HankeFactory.createRequest().withGeneratedOmistajat(1, 2).build()

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(request)
        // Check and record the Yhteystieto ids:
        assertThat(returnedHanke.omistajat).hasSize(2)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        assertThat(returnedHanke.omistajat[1].id).isNotNull
        val ytid1 = returnedHanke.omistajat[0].id!!

        // Clear all main fields (note, not id!) in the second yhteystieto:
        returnedHanke.omistajat[1] = clearYhteystieto(returnedHanke.omistajat[1])

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)

        // Check that one yhteystieto got removed, the first one remaining, and its fields not
        // affected:
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)

        // Check that the returned hanke has the same Yhteystieto:
        assertThat(returnedHanke3!!.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[0].nimi).isEqualTo(NAME_1)
    }

    @Test
    fun `test adding identical Yhteystietos in different groups and removing the other`() {
        // Setup Hanke with two identical Yhteystietos in different group:
        val request =
            HankeFactory.createRequest()
                .withGeneratedOmistaja(1)
                .withGeneratedRakennuttaja(1)
                .build()

        val returnedHanke = hankeService.createHanke(request)

        // Check and record the Yhteystieto ids, and that the ids are different:
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        assertThat(returnedHanke.rakennuttajat).hasSize(1)
        assertThat(returnedHanke.rakennuttajat[0].id).isNotNull
        val ytid1 = returnedHanke.omistajat[0].id!!
        val ytid2 = returnedHanke.rakennuttajat[0].id!!
        assertThat(ytid1).isNotEqualTo(ytid2)

        // Remove information from the rakennuttaja-yhteystieto:
        returnedHanke.rakennuttajat[0] = clearYhteystieto(returnedHanke.rakennuttajat[0])

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)

        // Check that rakennuttaja-yhteystieto got removed, the first one remaining, and its fields
        // not affected:
        assertThat(returnedHanke2.rakennuttajat).hasSize(0)
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)

        // Check that the returned hanke has the same 2 Yhteystietos:
        assertThat(returnedHanke3!!.rakennuttajat).hasSize(0)
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[0].nimi).isEqualTo(NAME_1)
    }

    @Test
    fun `test that sending the same Yhteystieto twice without id does not create duplicates`() {
        // Old version of the Yhteystieto should get removed, id increases in response,
        // get-operation returns the new one.
        // NOTE: UI is not supposed to do that, but this situation came up during
        // development/testing, so it is a sort of regression test for the logic.

        // Setup Hanke with one Yhteystieto:
        val request = HankeFactory.createRequest().withGeneratedOmistaja(1).build()

        val returnedHanke = hankeService.createHanke(request)

        // Check and record the Yhteystieto's id
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        val ytid = returnedHanke.omistajat[0].id

        // Tweaking the returned Yhteystieto-object's id back to null, to make it look like new one.
        returnedHanke.omistajat[0].id = null

        // Do an update (instead of create) with that "new" yhteystieto
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)

        // General checks (because using another API action)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        // Check that the returned hanke only has one entry, with a new id
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isNotNull
        assertThat(returnedHanke2.omistajat[0].id).isNotEqualTo(ytid)
        val ytid2 = returnedHanke2.omistajat[0].id

        // Use loadHanke and check it also returns only one entry
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)

        // Check that the returned hanke only has one entry, with that new id
        assertThat(returnedHanke3!!.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isNotNull
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid2)
    }

    @Test
    fun `test personal data logging`() {
        // Create hanke with two yhteystietos, save and check logs. There should be two rows and the
        // objectBefore fields should be null in them.
        // Setup Hanke with two Yhteystietos in the same group:
        val request = HankeFactory.createRequest().withGeneratedOmistajat(1, 2).build()

        val createdHanke = hankeService.createHanke(request)

        // Check and record the Yhteystieto ids, and to-be-changed field's value
        assertThat(createdHanke.omistajat).hasSize(2)
        assertThat(createdHanke.omistajat[0].id).isNotNull
        assertThat(createdHanke.omistajat[1].id).isNotNull
        val yhteystietoId1 = createdHanke.omistajat[0].id!!
        val yhteystietoId2 = createdHanke.omistajat[1].id!!
        assertThat(createdHanke.omistajat[1].nimi).isEqualTo(NAME_2)

        var auditLogEvents =
            auditLogRepository.findByType(ObjectType.YHTEYSTIETO).map { it.message.auditEvent }
        // The log must have 2 entries since two yhteystietos were created.
        assertThat(auditLogEvents.count()).isEqualTo(2)
        // Check that each yhteystieto has single entry in log:
        var auditLogEvents1 = auditLogEvents.filter { it.target.id == yhteystietoId1.toString() }
        var auditLogEvents2 = auditLogEvents.filter { it.target.id == yhteystietoId2.toString() }
        assertThat(auditLogEvents1).hasSize(1)
        assertThat(auditLogEvents2).hasSize(1)

        // Check that each entry has correct data.
        assertThat(auditLogEvents1[0].operation).isEqualTo(Operation.CREATE)
        assertThat(auditLogEvents1[0].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents1[0].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents1[0].status).isEqualTo(Status.SUCCESS)
        assertThat(auditLogEvents1[0].failureDescription).isNull()
        assertThat(auditLogEvents1[0].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents1[0].target.objectBefore).isNull()
        assertThat(auditLogEvents1[0].target.objectAfter).contains(NAME_1)

        assertThat(auditLogEvents2[0].operation).isEqualTo(Operation.CREATE)
        assertThat(auditLogEvents2[0].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents2[0].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents2[0].status).isEqualTo(Status.SUCCESS)
        assertThat(auditLogEvents2[0].failureDescription).isNull()
        assertThat(auditLogEvents2[0].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents2[0].target.objectBefore).isNull()
        assertThat(auditLogEvents2[0].target.objectAfter).contains(NAME_2)

        // Update one yhteystieto. This should create one update row in the log. ObjectBefore and
        // objectAfter fields should exist and have correct values.
        // Change a value:
        createdHanke.omistajat[1].nimi = NAME_SOMETHING
        // Call update, get the returned object, make some general checks:
        val hankeAfterUpdate = hankeService.updateHanke(createdHanke)
        assertThat(hankeAfterUpdate).isNotNull
        assertThat(hankeAfterUpdate).isNotSameAs(createdHanke)
        assertThat(hankeAfterUpdate.id).isNotNull
        // Check that both entries kept their ids, and the only change is where expected
        assertThat(hankeAfterUpdate.omistajat).hasSize(2)
        assertThat(hankeAfterUpdate.omistajat[0].id).isEqualTo(yhteystietoId1)
        assertThat(hankeAfterUpdate.omistajat[1].id).isEqualTo(yhteystietoId2)
        assertThat(hankeAfterUpdate.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(hankeAfterUpdate.omistajat[1].nimi).isEqualTo(NAME_SOMETHING)

        auditLogEvents =
            auditLogRepository.findByType(ObjectType.YHTEYSTIETO).map { it.message.auditEvent }
        // Check that only 1 entry was added to log, about the updated yhteystieto.
        assertThat(auditLogEvents).hasSize(3)
        // Check that the second yhteystieto got a single entry in log and the other didn't.
        auditLogEvents1 = auditLogEvents.filter { it.target.id == yhteystietoId1.toString() }
        auditLogEvents2 = auditLogEvents.filter { it.target.id == yhteystietoId2.toString() }
        assertThat(auditLogEvents1).hasSize(1)
        assertThat(auditLogEvents2).hasSize(2)
        // Check that the new entry has correct data.
        assertThat(auditLogEvents2[1].operation).isEqualTo(Operation.UPDATE)
        assertThat(auditLogEvents2[1].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents2[1].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents2[1].status).isEqualTo(Status.SUCCESS)
        assertThat(auditLogEvents2[1].failureDescription).isNull()
        assertThat(auditLogEvents2[1].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents2[1].target.objectBefore).contains(NAME_2)
        assertThat(auditLogEvents2[1].target.objectAfter).contains(NAME_SOMETHING)

        // Delete the other yhteystieto. This should create one update in log, with null
        // objectAfter.
        hankeAfterUpdate.omistajat[1] = clearYhteystieto(hankeAfterUpdate.omistajat[1])
        // Call update, get the returned object:
        val hankeAfterDelete = hankeService.updateHanke(hankeAfterUpdate)
        // Check that first yhteystieto remains, second one got removed:
        assertThat(hankeAfterDelete.omistajat).hasSize(1)
        assertThat(hankeAfterDelete.omistajat[0].id).isEqualTo(yhteystietoId1)
        assertThat(hankeAfterDelete.omistajat[0].nimi).isEqualTo(NAME_1)

        auditLogEvents =
            auditLogRepository.findByType(ObjectType.YHTEYSTIETO).map { it.message.auditEvent }
        // Check that only 1 entry was added to log, about the deleted yhteystieto.
        assertThat(auditLogEvents).hasSize(4)
        // Check that the second yhteystieto got a single entry in log and the other didn't.
        auditLogEvents1 = auditLogEvents.filter { it.target.id == yhteystietoId1.toString() }
        auditLogEvents2 = auditLogEvents.filter { it.target.id == yhteystietoId2.toString() }
        assertThat(auditLogEvents1).hasSize(1)
        assertThat(auditLogEvents2).hasSize(3)
        // Check that the new entry has correct data.
        assertThat(auditLogEvents2[2].operation).isEqualTo(Operation.DELETE)
        assertThat(auditLogEvents2[2].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents2[2].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents2[2].status).isEqualTo(Status.SUCCESS)
        assertThat(auditLogEvents2[2].failureDescription).isNull()
        assertThat(auditLogEvents2[2].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents2[2].target.objectBefore).contains(NAME_SOMETHING)
        assertThat(auditLogEvents2[2].target.objectAfter).isNull()
    }

    @Test
    @Transactional // due to lazy initialized fields being accessed
    fun `test personal data processing restriction`() {
        // Setup Hanke with two Yhteystietos in different groups. The test will only manipulate the
        // rakennuttaja.
        val request =
            HankeFactory.createRequest()
                .withGeneratedOmistaja(1)
                .withGeneratedRakennuttaja(2)
                .build()
        // Call create, get the return object:
        val hanke = hankeService.createHanke(request)
        // Logs must have 2 entries (two yhteystietos were created):
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(2)

        // Get the non-owner yhteystieto, and set the processing restriction (i.e. locked) -flag
        // (must be done via entities):
        // Fetching the yhteystieto is a bit clumsy since we don't have separate a
        // YhteystietoRepository.
        var hankeEntity = hankeRepository.findById(hanke.id).get()
        var yhteystietos = hankeEntity.listOfHankeYhteystieto
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

        assertThatExceptionOfType(HankeYhteystietoProcessingRestrictedException::class.java)
            .isThrownBy { hankeService.updateHanke(hankeWithLockedYT) }
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
        assertThat(auditLogEvents[1].target.objectBefore).contains(NAME_2)
        assertThat(auditLogEvents[1].target.objectAfter).contains("Muhaha-Evil-Change")

        // Try to delete the yhteystieto. It should fail and add a new log entry.
        hankeWithLockedYT.rakennuttajat[0] = clearYhteystieto(hankeWithLockedYT.rakennuttajat[0])
        assertThatExceptionOfType(HankeYhteystietoProcessingRestrictedException::class.java)
            .isThrownBy { hankeService.updateHanke(hankeWithLockedYT) }
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
        assertThat(auditLogEvents[2].target.objectBefore).contains(NAME_2)
        assertThat(auditLogEvents[2].target.objectAfter).isNull()

        // Check that both yhteystietos still exist and the values have not gotten changed.
        val returnedHankeAfterBlockedActions = hankeService.loadHanke(hanke.hankeTunnus)
        val rakennuttajat = returnedHankeAfterBlockedActions!!.rakennuttajat
        assertThat(rakennuttajat).hasSize(1)
        assertThat(rakennuttajat[0].nimi).isEqualTo(NAME_2)

        // Unset the processing restriction flag:
        hankeEntity = hankeRepository.findById(hanke.id).get()
        yhteystietos = hankeEntity.listOfHankeYhteystieto
        rakennuttajaEntity = yhteystietos.filter { it.contactType == ContactType.RAKENNUTTAJA }[0]
        rakennuttajaEntity.dataLocked = false
        hankeRepository.save(hankeEntity)

        // Updating the yhteystieto should now work:
        val hankeWithUnlockedYT = hankeService.loadHanke(hanke.hankeTunnus)
        hankeWithUnlockedYT!!.rakennuttajat[0].nimi = "Hopefully-Not-Evil-Change"
        val finalHanke = hankeService.updateHanke(hankeWithUnlockedYT)

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
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME,
                kaistaPituusHaitta = KaistajarjestelynPituus.KOLME,
                meluHaitta = Haitta13.KOLME,
                polyHaitta = Haitta13.KOLME,
                tarinaHaitta = Haitta13.KOLME,
            )
        val request = HankeFactory.createRequest().withHankealue().withHankealue(hankealue).build()

        val createdHanke = hankeService.createHanke(request)

        assertThat(createdHanke.alueet).hasSize(2)
        val alue = createdHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(alkuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(loppuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME)
        assertThat(alue.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.KOLME)
        assertThat(alue.meluHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.polyHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.tarinaHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.geometriat).isNotNull
    }

    @Nested
    inner class GenerateHankeWithApplication {

        @Test
        fun `generates hanke based on application`() {
            val inputApplication = AlluDataFactory.cableReportWithoutHanke()

            val application = hankeService.generateHankeWithApplication(inputApplication, USER_NAME)

            assertThat(application.applicationData.name)
                .isEqualTo(inputApplication.applicationData.name)
            val hanke = hankeRepository.findByHankeTunnus(application.hankeTunnus)!!
            assertThat(hanke.generated).isTrue
            assertThat(hanke.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(hanke.hankeTunnus).isEqualTo(application.hankeTunnus)
            assertThat(hanke.nimi).isEqualTo(application.applicationData.name)
            assertThat(hanke.perustaja?.nimi).isEqualTo("Teppo Testihenkilö")
            assertThat(hanke.perustaja?.email).isEqualTo("teppo@example.test")
        }

        @Test
        fun `sets hanke name according to limit and saves successfully`() {
            val expectedName = "a".repeat(MAXIMUM_HANKE_NIMI_LENGTH)
            val tooLongName = expectedName + "bbb"
            val inputApplication =
                AlluDataFactory.cableReportWithoutHanke()
                    .copy(
                        applicationData =
                            AlluDataFactory.createCableReportApplicationData(name = tooLongName)
                    )

            val application = hankeService.generateHankeWithApplication(inputApplication, USER_NAME)

            val hanke = hankeRepository.findByHankeTunnus(application.hankeTunnus)!!
            assertThat(hanke.nimi).isEqualTo(expectedName)
            assertThat(hanke.generated).isTrue
            assertThat(hanke.status).isEqualTo(HankeStatus.DRAFT)
            assertThat(hanke.hankeTunnus).isEqualTo(application.hankeTunnus)
            assertThat(hanke.perustaja?.nimi).isEqualTo("Teppo Testihenkilö")
            assertThat(hanke.perustaja?.email).isEqualTo("teppo@example.test")
        }

        @Test
        fun `creates hankealueet from the application areas`() {
            val inputApplication =
                AlluDataFactory.cableReportWithoutHanke(
                    AlluDataFactory.createCableReportApplicationData(areas = null)
                        .withArea(name = "Area", geometry = GeometriaFactory.secondPolygon)
                )

            val application = hankeService.generateHankeWithApplication(inputApplication, USER_NAME)

            val hanke = hankeService.loadHanke(application.hankeTunnus)!!
            assertThat(hanke.alueet).hasSize(1)
            assertk.assertThat(hanke.alueet).first().all {
                prop(SavedHankealue::nimi).isEqualTo("Hankealue 1")
                hasSingleGeometryWithCoordinates(GeometriaFactory.secondPolygon)
            }
        }

        @Test
        fun `rolls back when application service throws an exception`() {
            // Use an intersecting geometry so that ApplicationService will throw an exception
            val inputApplication =
                AlluDataFactory.cableReportWithoutHanke {
                    withArea(
                        "area",
                        "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json"
                            .asJsonResource()
                    )
                }

            assertThrows<ApplicationGeometryException> {
                hankeService.generateHankeWithApplication(inputApplication, USER_NAME)
            }

            assertThat(hankeRepository.findAll()).isEmpty()
        }
    }

    @Test
    fun `updateHanke creates new hankealue`() {
        val alkuPvm = DateFactory.getStartDatetime()
        val loppuPvm = DateFactory.getStartDatetime()
        val createdHanke = hankeFactory.createRequest().withHankealue().save()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = loppuPvm,
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME,
                kaistaPituusHaitta = KaistajarjestelynPituus.KOLME,
                meluHaitta = Haitta13.KOLME,
                polyHaitta = Haitta13.KOLME,
                tarinaHaitta = Haitta13.KOLME,
            )
        createdHanke.alueet.add(hankealue)

        val updatedHanke = hankeService.updateHanke(createdHanke)

        assertThat(updatedHanke.alueet).hasSize(2)
        val alue = updatedHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(alkuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(loppuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME)
        assertThat(alue.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.KOLME)
        assertThat(alue.meluHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.polyHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.tarinaHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.geometriat).isNotNull
    }

    @Test
    fun `updateHanke new hankealue name updates name and keeps data intact`() {
        val createdHanke = hankeFactory.createRequest().withHankealue().save()
        val hankealue = createdHanke.alueet[0]
        assertNull(hankealue.nimi)
        val modifiedHanke =
            createdHanke.copy().apply {
                // manually set mutable collections due to a shallow copy.
                this.omistajat = createdHanke.omistajat
                this.rakennuttajat = createdHanke.rakennuttajat
                this.toteuttajat = createdHanke.toteuttajat
                this.tyomaaTyyppi = createdHanke.tyomaaTyyppi
                this.alueet = mutableListOf(hankealue.copy(nimi = "Changed Name"))
            }

        val updateHankeResult = hankeService.updateHanke(modifiedHanke)

        assertThat(updateHankeResult)
            .usingRecursiveComparison()
            .ignoringFields("modifiedAt", "modifiedBy", "version", "alueet")
            .isEqualTo(createdHanke)
        assertThat(updateHankeResult.alueet).hasSize(1)
        val resultAlue = updateHankeResult.alueet[0]
        assertThat(resultAlue)
            .usingRecursiveComparison()
            .ignoringFields("nimi", "geometriat")
            .isEqualTo(hankealue)
        assertThat(resultAlue.geometriat?.featureCollection)
            .isNotNull
            .isEqualTo(hankealue.geometriat?.featureCollection)
        assertThat(resultAlue.nimi).isEqualTo("Changed Name")
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
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME,
                kaistaPituusHaitta = KaistajarjestelynPituus.KOLME,
                meluHaitta = Haitta13.KOLME,
                polyHaitta = Haitta13.KOLME,
                tarinaHaitta = Haitta13.KOLME,
            )
        val hanke = hankeFactory.createRequest().withHankealue().withHankealue(hankealue).save()
        assertThat(hanke.alueet).hasSize(2)
        assertThat(hankealueCount()).isEqualTo(2)
        assertThat(geometriatCount()).isEqualTo(2)
        hanke.alueet.removeAt(0)

        val updatedHanke = hankeService.updateHanke(hanke)

        assertThat(updatedHanke.alueet).hasSize(1)
        val alue = updatedHanke.alueet[0]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(alkuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(loppuPvm.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME)
        assertThat(alue.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.KOLME)
        assertThat(alue.meluHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.polyHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.tarinaHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.geometriat).isNotNull
        val hankeFromDb = hankeService.loadHanke(hanke.hankeTunnus)
        assertThat(hankeFromDb?.alueet).hasSize(1)
        assertThat(hankealueCount()).isEqualTo(1)
        assertThat(geometriatCount()).isEqualTo(1)
    }

    @Test
    fun `deleteHanke creates audit log entry for deleted hanke`() {
        val hanke = hankeFactory.createRequest().withHankealue().save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()

        hankeService.deleteHanke(hanke.hankeTunnus, "testUser")

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val hankeLog = hankeLogs[0]
        assertFalse(hankeLog.isSent)
        assertThat(hankeLog.createdAt).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        val event = hankeLog.message.auditEvent
        assertThat(event.dateTime).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
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
                tormaystarkasteluTulos = true,
            )
        JSONAssert.assertEquals(
            expectedObject,
            event.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `deleteHanke creates audit log entries for deleted yhteystiedot`() {
        val hanke = hankeFactory.createRequest().withYhteystiedot().save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()

        hankeService.deleteHanke(hanke.hankeTunnus, "testUser")

        val logs = auditLogRepository.findByType(ObjectType.YHTEYSTIETO)
        assertEquals(4, logs.size)
        val deleteLogs = logs.filter { it.message.auditEvent.operation == Operation.DELETE }
        assertk.assertThat(deleteLogs).hasSize(4)
        assertk.assertThat(deleteLogs).each { log ->
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
    fun `deleteHanke hanke when no hakemus should delete hanke`() {
        val hanke = hankeFactory.save()

        hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)

        assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
    }

    @Test
    fun `deleteHanke when hakemus is pending should delete hanke`() {
        val hakemusAlluId = 356
        val hanke = initHankeWithHakemus(hakemusAlluId)
        every { cableReportService.getApplicationInformation(hakemusAlluId) } returns
            AlluDataFactory.createAlluApplicationResponse(status = ApplicationStatus.PENDING)
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
    fun `deleteHanke hakemus is not pending should throw`() {
        val hakemusAlluId = 123
        val hanke = initHankeWithHakemus(hakemusAlluId)
        every { cableReportService.getApplicationInformation(hakemusAlluId) } returns
            AlluDataFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

        assertThrows<HankeAlluConflictException> {
            hankeService.deleteHanke(hanke.hankeTunnus, USER_NAME)
        }

        assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNotNull
        verify { cableReportService.getApplicationInformation(hakemusAlluId) }
    }

    @Test
    fun `deleteHanke when hanke has users should remove users and tokens`() {
        val hanketunnus = hankeFactory.createRequest().withYhteystiedot().save().hankeTunnus
        assertk.assertThat(hankeKayttajaRepository.findAll()).hasSize(4)
        assertk.assertThat(kayttajaTunnisteRepository.findAll()).hasSize(4)

        hankeService.deleteHanke(hanketunnus, USER_NAME)

        assertk.assertThat(hankeRepository.findAll()).isEmpty()
        assertk.assertThat(hankeKayttajaRepository.findAll()).isEmpty()
        assertk.assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
    }

    @Test
    fun `createHanke creates audit log entry for created hanke`() {
        TestUtils.addMockedRequestIp()

        val hanke = hankeFactory.createRequest().withHankealue().save()

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val hankeLog = hankeLogs[0]
        assertFalse(hankeLog.isSent)
        assertThat(hankeLog.createdAt).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        val event = hankeLog.message.auditEvent
        assertThat(event.dateTime).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        assertEquals(Operation.CREATE, event.operation)
        assertEquals(Status.SUCCESS, event.status)
        assertNull(event.failureDescription)
        assertEquals("1", event.appVersion)
        assertEquals(USER_NAME, event.actor.userId)
        assertEquals(UserRole.USER, event.actor.role)
        assertEquals(TestUtils.mockedIp, event.actor.ipAddress)
        assertEquals(hanke.id.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        assertNull(event.target.objectBefore)
        val expectedObject =
            expectedHankeLogObject(hanke, hanke.alueet[0], tormaystarkasteluTulos = true)
        JSONAssert.assertEquals(
            expectedObject,
            event.target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `createHanke without a hankealue creates audit log entry for created hanke`() {
        TestUtils.addMockedRequestIp()
        val request =
            HankeFactory.createRequest(
                    tyomaaKatuosoite = "Testikatu 1",
                    tyomaaTyyppi = setOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU),
                )
                .build()

        val hanke = hankeService.createHanke(request)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val hankeLog = hankeLogs[0]
        assertFalse(hankeLog.isSent)
        assertThat(hankeLog.createdAt).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        val event = hankeLog.message.auditEvent
        assertThat(event.dateTime).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        assertEquals(Operation.CREATE, event.operation)
        assertEquals(Status.SUCCESS, event.status)
        assertNull(event.failureDescription)
        assertEquals("1", event.appVersion)
        assertEquals(USER_NAME, event.actor.userId)
        assertEquals(UserRole.USER, event.actor.role)
        assertEquals(TestUtils.mockedIp, event.actor.ipAddress)
        assertEquals(hanke.id.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        assertNull(event.target.objectBefore)
        val expectedObject = expectedHankeLogObject(hanke, alkuPvm = null, loppuPvm = null)
        JSONAssert.assertEquals(
            expectedObject,
            event.target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `updateHanke creates audit log entry for updated hanke`() {
        val hanke =
            hankeFactory
                .createRequest(
                    tyomaaKatuosoite = "Testikatu 1",
                    tyomaaTyyppi = mutableSetOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU),
                )
                .save()
        val geometria = GeometriaFactory.create().apply { id = 67 }
        hanke.alueet.add(HankealueFactory.create(id = null, geometriat = geometria))
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()

        val updatedHanke = hankeService.updateHanke(hanke)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val hankeLog = hankeLogs[0]
        assertFalse(hankeLog.isSent)
        assertThat(hankeLog.createdAt).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        val event = hankeLog.message.auditEvent
        assertThat(event.dateTime).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
        assertEquals(Operation.UPDATE, event.operation)
        assertEquals(Status.SUCCESS, event.status)
        assertNull(event.failureDescription)
        assertEquals("1", event.appVersion)
        assertEquals("test7358", event.actor.userId)
        assertEquals(UserRole.USER, event.actor.role)
        assertEquals(TestUtils.mockedIp, event.actor.ipAddress)
        assertEquals(hanke.id.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        val expectedObjectBefore = expectedHankeLogObject(hanke, alkuPvm = null, loppuPvm = null)
        JSONAssert.assertEquals(
            expectedObjectBefore,
            event.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val expectedObjectAfter =
            expectedHankeLogObject(
                hanke,
                updatedHanke.alueet[0],
                hankeVersion = 1,
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
        val hanke = hankeFactory.createRequest().withHankealue().save()
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

        val updatedHanke = hankeService.updateHanke(hanke)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertEquals(1, hankeLogs.size)
        val event = hankeLogs[0].message.auditEvent
        assertEquals(Operation.UPDATE, event.operation)
        assertEquals(hanke.id.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        val expectedObjectBefore =
            expectedHankeLogObject(hanke, hanke.alueet[0], tormaystarkasteluTulos = true)
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
                hankeVersion = 1,
                geometriaVersion = 1,
                tormaystarkasteluTulos = true,
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
                .createRequest(
                    tyomaaKatuosoite = "Testikatu 1",
                    tyomaaTyyppi = mutableSetOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU),
                )
                .save()
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())

        hankeService.updateHanke(hanke)

        val hankeLogs = auditLogRepository.findByType(ObjectType.HANKE)
        assertThat(hankeLogs).hasSize(1)
        val target = hankeLogs[0]!!.message.auditEvent.target
        val expectedObjectBefore = expectedHankeLogObject(hanke, alkuPvm = null, loppuPvm = null)
        JSONAssert.assertEquals(
            expectedObjectBefore,
            target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val expectedObjectAfter =
            expectedHankeLogObject(hanke, hankeVersion = 1, alkuPvm = null, loppuPvm = null)
        JSONAssert.assertEquals(
            expectedObjectAfter,
            target.objectAfter,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `update hanke enforces generated to be false`() {
        val hanke = hankeFactory.save()
        assertThat(hanke.generated).isFalse()

        val result = hankeService.updateHanke(hanke.copy(generated = true))

        assertFalse(result.generated)
    }

    private fun verifyYhteyshenkilot(yhteyshenkilot: List<Yhteyshenkilo>) {
        assertThat(yhteyshenkilot).isNotEmpty
        yhteyshenkilot.forEach {
            assertThat(it.etunimi).isNotBlank
            assertThat(it.sukunimi).isNotBlank
            assertThat(it.email).isNotBlank
            assertThat(it.puhelinnumero).isNotBlank
        }
    }

    private fun initHankeWithHakemus(alluId: Int): HankeEntity {
        val hanke = hankeFactory.saveMinimal(hankeTunnus = "HAI23-1")
        val application =
            applicationRepository.save(
                AlluDataFactory.createApplicationEntity(
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
        assertThat(hanke.alueet).isNotEmpty
        hanke.alueet.forEach { alue ->
            val features = alue.geometriat?.featureCollection?.features
            assertThat(features).isNotEmpty
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
          "ytunnus": $defaultYtunnus,
          "puhelinnumero":"010$i$i$i$i$i$i$i",
          "organisaatioNimi":"org$i",
          "osasto":"osasto$i",
          "rooli": "Isännöitsijä$i",
          "tyyppi": "YHTEISO",
          "alikontaktit": ${expectedYhteyshenkilot(i)}
        }"""

    private fun expectedYhteyshenkilot(i: Int) =
        """[{
             "etunimi": "yhteys-etu$i",
             "sukunimi": "yhteys-suku$i",
             "email": "yhteys-email$i",
             "puhelinnumero": "010$i$i$i$i$i$i$i"
            }]"""

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
}
