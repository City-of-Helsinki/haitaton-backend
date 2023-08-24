package fi.hel.haitaton.hanke

import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.application.ALLU_USER_CANCELLATION_MSG
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YHTEISO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YKSITYISHENKILO
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi.YRITYS
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedOmistajat
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.geometria.Geometriat
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
import org.testcontainers.junit.jupiter.Testcontainers

private const val USER_NAME = "test7358"
private const val NAME_1 = "etu1 suku1"
private const val NAME_2 = "etu2 suku2"
private const val NAME_3 = "etu3 suku3"
private const val NAME_4 = "etu4 suku4"
private const val NAME_SOMETHING = "Som Et Hing"

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(cableReportService)
    }

    @Test
    fun `create Hanke with full data set succeeds and returns a new domain object with the correct values`() {
        val hanke: Hanke = getATestHanke().withYhteystiedot { it.id = null }.withHankealue()

        val datetimeAlku = hanke.alueet[0].haittaAlkuPvm // nextyear.2.20 23:45:56Z
        val datetimeLoppu = hanke.alueet[0].haittaLoppuPvm // nextyear.2.21 0:12:34Z
        // For checking audit field datetimes (with some minutes of margin for test running delay):
        val currentDatetime = getCurrentTimeUTC()

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(hanke)

        // Verify privileges
        PermissionCode.values().forEach {
            assertThat(permissionService.hasPermission(returnedHanke.id!!, USER_NAME, it)).isTrue()
        }
        // Check the return object in general:
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check the fields:
        // Note, "pvm" values should have become truncated to begin of the day
        val expectedDateAlku = // nextyear.2.20 00:00:00Z
            datetimeAlku!!.truncatedTo(ChronoUnit.DAYS)
        val expectedDateLoppu = // nextyear.2.21 00:00:00Z
            datetimeLoppu!!.truncatedTo(ChronoUnit.DAYS)
        assertThat(returnedHanke.status).isEqualTo(HankeStatus.PUBLIC)
        assertThat(returnedHanke.nimi).isEqualTo("Hämeentien perusparannus ja katuvalot")
        assertThat(returnedHanke.kuvaus).isEqualTo("lorem ipsum dolor sit amet...")
        assertThat(returnedHanke.alkuPvm).isEqualTo(expectedDateAlku)
        assertThat(returnedHanke.loppuPvm).isEqualTo(expectedDateLoppu)
        assertThat(returnedHanke.vaihe).isEqualTo(Vaihe.SUUNNITTELU)
        assertThat(returnedHanke.suunnitteluVaihe).isEqualTo(SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS)
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
        assertThat(omistaja).isNotNull
        assertThat(rakennuttaja).isNotNull
        assertThat(toteuttaja).isNotNull
        assertThat(muu).isNotNull
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
        val hanke = hankeService.createHanke(getATestHanke())

        val hankeEntity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)!!
        assertThat(hankeEntity.perustaja).isNull()
        assertThat(hankeKayttajaRepository.findAll()).isEmpty()
        assertThat(kayttajaTunnisteRepository.findAll()).isEmpty()
    }

    @Test
    fun `create Hanke with partial data set and update with full data set give correct status`() {
        // Setup Hanke (without any yhteystieto):
        val hanke: Hanke = getATestHanke()
        // Also null one mandatory simple field:
        hanke.tyomaaKatuosoite = null

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(hanke)

        // Check the return object in general:
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull

        // Check status:
        assertThat(returnedHanke.status).isEqualTo(HankeStatus.DRAFT)

        // Fill the values
        returnedHanke.tyomaaKatuosoite = "Testikatu 1 A 1"
        returnedHanke.withYhteystiedot { it.id = null }

        // Call update
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)

        // Check the return object in general:
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check that status changed now with full data available:
        assertThat(returnedHanke2.status).isEqualTo(HankeStatus.PUBLIC)
    }

    @Test
    fun `createHanke ignores the status field in the given hanke`() {
        // Setup Hanke (without any yhteystieto):
        val hanke = getATestHanke()
        hanke.status = HankeStatus.PUBLIC

        val returnedHanke = hankeService.createHanke(hanke)

        assertThat(returnedHanke.status).isEqualTo(HankeStatus.DRAFT)
    }

    @Test
    fun `createHanke ignores the status field in the given hanke for hanke with all information`() {
        // Setup Hanke (with all mandatory fields):
        val hanke = getATestHanke().withYhteystiedot { it.id = null }
        hanke.status = HankeStatus.DRAFT

        val returnedHanke = hankeService.createHanke(hanke)

        assertThat(returnedHanke.status).isEqualTo(HankeStatus.PUBLIC)
    }

    @Test
    fun `createHanke resets feature properties`() {
        val hanke = getATestHanke().withHankealue()
        hanke.alueet[0].geometriat?.featureCollection?.features?.forEach {
            it.properties["something"] = "fishy"
        }

        val result = hankeService.createHanke(hanke)

        assertFeaturePropertiesIsReset(result, mutableMapOf("hankeTunnus" to hanke.hankeTunnus))
    }

    @Test
    fun `getHankeHakemuksetPair maps hanke and hakemukset to a pair correctly`() {
        val hanke = initHankeWithHakemus(123)

        val result = hankeService.getHankeWithApplications(hanke.hankeTunnus!!)

        val expectedHanke = hanke.toDomainObject().apply { tyomaaTyyppi = hanke.tyomaaTyyppi }
        val expectedHakemus = applicationRepository.findAll().first().toDomainObject()
        assertThat(result.hanke).usingRecursiveComparison().isEqualTo(expectedHanke)
        assertThat(result.applications).hasSameElementsAs(listOf(expectedHakemus))
    }

    @Test
    fun `getHankeHakemuksetPair hanke does not exist throws not found`() {
        val exception =
            assertThrows<HankeNotFoundException> {
                hankeService.getHankeWithApplications("HAI-1234")
            }

        assertThat(exception).hasMessage("Hanke HAI-1234 not found")
    }

    @Test
    fun `getHankeHakemuksetPair when no hakemukset returns hanke and empty list`() {
        val hankeInitial = hankeService.createHanke(HankeFactory.create())

        val result = hankeService.getHankeWithApplications(hankeInitial.hankeTunnus!!)

        with(result) {
            assertThat(hanke).usingRecursiveComparison().isEqualTo(hankeInitial)
            assertTrue(applications.isEmpty())
        }
    }

    @Test
    fun `updateHanke resets feature properties`() {
        val hanke = getATestHanke().withHankealue()
        val createdHanke = hankeService.createHanke(hanke)
        val updatedHanke =
            createdHanke.apply {
                this.alueet[0].geometriat?.featureCollection?.features?.forEach {
                    it.properties["something"] = "fishy"
                }
            }

        val result = hankeService.updateHanke(updatedHanke)

        assertFeaturePropertiesIsReset(result, mutableMapOf("hankeTunnus" to hanke.hankeTunnus))
    }

    @Test
    fun `updateHanke ignores the status field in the given hanke`() {
        // Setup Hanke (without any yhteystieto):
        val hanke = hankeService.createHanke(getATestHanke())
        hanke.status = HankeStatus.PUBLIC

        val returnedHanke = hankeService.updateHanke(hanke)

        assertThat(returnedHanke.status).isEqualTo(HankeStatus.DRAFT)
    }

    @Test
    fun `updateHanke doesn't revert to a draft`() {
        // Setup Hanke (with all mandatory fields):
        val hanke = hankeService.createHanke(getATestHanke().withYhteystiedot { it.id = null })
        assertThat(hanke.status).isEqualTo(HankeStatus.PUBLIC)
        hanke.tyomaaKatuosoite = ""

        val exception = assertThrows<HankeArgumentException> { hankeService.updateHanke(hanke) }

        assertThat(exception).hasMessage("A public hanke didn't have all mandatory fields filled.")
    }

    @Test
    fun `test adding a new Yhteystieto to a group that already has one and to another group`() {
        // Also tests how update affects audit fields.

        // Setup Hanke with one Yhteystieto:
        val hanke: Hanke = getATestHanke().withGeneratedOmistaja(1) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check and record the Yhteystieto's id
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        val ytid = returnedHanke.omistajat[0].id!!

        returnedHanke
            .withGeneratedOmistaja(2) { it.id = null }
            .withGeneratedRakennuttaja(3) { it.id = null }

        // For checking audit field datetimes (with some minutes of margin for test running delay):
        val currentDatetime = getCurrentTimeUTC()

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(hanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

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
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke has the same 3 Yhteystietos:
        assertThat(returnedHanke3.omistajat).hasSize(2)
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
        val hanke: Hanke = getATestHanke().withGeneratedOmistajat(1, 2) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
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
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(hanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check that both entries kept their ids, and the only change is where expected
        assertThat(returnedHanke2.omistajat).hasSize(2)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[1].id).isEqualTo(ytid2)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)
        assertThat(returnedHanke2.omistajat[1].nimi).isEqualTo(NAME_SOMETHING)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke has the same 2 Yhteystietos:
        assertThat(returnedHanke3.omistajat).hasSize(2)
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
        val hanke: Hanke = getATestHanke().withGeneratedOmistajat(1, 2) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check and record the Yhteystieto ids:
        assertThat(returnedHanke.omistajat).hasSize(2)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        assertThat(returnedHanke.omistajat[1].id).isNotNull
        val ytid1 = returnedHanke.omistajat[0].id!!

        // Clear all main fields (note, not id!) in the second yhteystieto:
        returnedHanke.omistajat[1].nimi = ""
        returnedHanke.omistajat[1].email = ""
        returnedHanke.omistajat[1].puhelinnumero = ""
        returnedHanke.omistajat[1].organisaatioNimi = ""
        returnedHanke.omistajat[1].osasto = ""

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(hanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check that one yhteystieto got removed, the first one remaining, and its fields not
        // affected:
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke has the same Yhteystieto:
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[0].nimi).isEqualTo(NAME_1)
    }

    @Test
    fun `test adding identical Yhteystietos in different groups and removing the other`() {
        // Setup Hanke with two identical Yhteystietos in different group:
        val hanke: Hanke =
            getATestHanke()
                .withGeneratedOmistaja(1) { it.id = null }
                .withGeneratedRakennuttaja(1) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check and record the Yhteystieto ids, and that the ids are different:
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        assertThat(returnedHanke.rakennuttajat).hasSize(1)
        assertThat(returnedHanke.rakennuttajat[0].id).isNotNull
        val ytid1 = returnedHanke.omistajat[0].id!!
        val ytid2 = returnedHanke.rakennuttajat[0].id!!
        assertThat(ytid1).isNotEqualTo(ytid2)

        // Remove the rakennuttaja-yhteystieto:
        returnedHanke.rakennuttajat[0].nimi = ""
        returnedHanke.rakennuttajat[0].email = ""
        returnedHanke.rakennuttajat[0].puhelinnumero = ""
        returnedHanke.rakennuttajat[0].organisaatioNimi = ""
        returnedHanke.rakennuttajat[0].osasto = ""

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(hanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check that rakennuttaja-yhteystieto got removed, the first one remaining, and its fields
        // not
        // affected:
        assertThat(returnedHanke2.rakennuttajat).hasSize(0)
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].nimi).isEqualTo(NAME_1)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke has the same 2 Yhteystietos:
        assertThat(returnedHanke3.rakennuttajat).hasSize(0)
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
        val hanke: Hanke = getATestHanke().withGeneratedOmistaja(1) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check and record the Yhteystieto's id
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        val ytid = returnedHanke.omistajat[0].id

        // Tweaking the returned Yhteystieto-object's id back to null, to make it look like new one.
        returnedHanke.omistajat[0].id = null

        // Do an update (instead of create) with that "new" yhteystieto
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)

        // General checks (because using another API action)
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull
        assertThat(returnedHanke2.hankeTunnus).isNotNull
        // Check that the returned hanke only has one entry, with a new id
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isNotNull
        assertThat(returnedHanke2.omistajat[0].id).isNotEqualTo(ytid)
        val ytid2 = returnedHanke2.omistajat[0].id

        // Use loadHanke and check it also returns only one entry
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke only has one entry, with that new id
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isNotNull
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid2)
    }

    @Test
    fun `test personal data logging`() {
        // Create hanke with two yhteystietos, save and check logs. There should be two rows and the
        // objectBefore fields should be null in them.
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke: Hanke = getATestHanke().withGeneratedOmistajat(1, 2) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val createdHanke = hankeService.createHanke(hanke)
        assertThat(createdHanke).isNotNull
        assertThat(createdHanke).isNotSameAs(hanke)
        assertThat(createdHanke.id).isNotNull
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
        assertThat(hankeAfterUpdate).isNotSameAs(hanke)
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
        hankeAfterUpdate.omistajat[1].apply {
            nimi = ""
            puhelinnumero = ""
            email = ""
            organisaatioNimi = ""
            osasto = ""
        }
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
        val hanke: Hanke =
            getATestHanke()
                .withGeneratedOmistaja(1) { it.id = null }
                .withGeneratedRakennuttaja(2) { it.id = null }
        // Call create, get the return object:
        val returnedHanke = hankeService.createHanke(hanke)
        // Logs must have 2 entries (two yhteystietos were created):
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(2)

        // Get the non-owner yhteystieto, and set the processing restriction (i.e. locked) -flag
        // (must be done via entities):
        // Fetching the yhteystieto is a bit clumsy since we don't have separate a
        // YhteystietoRepository.
        val hankeId = returnedHanke.id
        var hankeEntity = hankeRepository.findById(hankeId!!).get()
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
        val hankeWithLockedYT = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
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
        hankeWithLockedYT.rakennuttajat[0].apply {
            nimi = ""
            puhelinnumero = ""
            email = ""
            organisaatioNimi = ""
            osasto = ""
        }
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
        val returnedHankeAfterBlockedActions = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        val rakennuttajat = returnedHankeAfterBlockedActions!!.rakennuttajat
        assertThat(rakennuttajat).hasSize(1)
        assertThat(rakennuttajat[0].nimi).isEqualTo(NAME_2)

        // Unset the processing restriction flag:
        hankeEntity = hankeRepository.findById(hankeId).get()
        yhteystietos = hankeEntity.listOfHankeYhteystieto
        rakennuttajaEntity = yhteystietos.filter { it.contactType == ContactType.RAKENNUTTAJA }[0]
        rakennuttajaEntity.dataLocked = false
        hankeRepository.save(hankeEntity)

        // Updating the yhteystieto should now work:
        val hankeWithUnlockedYT = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
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
        val hanke = getATestHanke()
        val hankealue =
            HankealueFactory.create(
                haittaAlkuPvm = hanke.alkuPvm,
                haittaLoppuPvm = hanke.loppuPvm,
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME,
                kaistaPituusHaitta = KaistajarjestelynPituus.KOLME,
                meluHaitta = Haitta13.KOLME,
                polyHaitta = Haitta13.KOLME,
                tarinaHaitta = Haitta13.KOLME,
            )
        hanke.alueet.add(hankealue)

        val createdHanke = hankeService.createHanke(hanke)

        assertThat(createdHanke.alueet).hasSize(2)
        val alue = createdHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(hanke.alkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(hanke.loppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME)
        assertThat(alue.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.KOLME)
        assertThat(alue.meluHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.polyHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.tarinaHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.geometriat).isNotNull
    }

    @Test
    fun `generateHankeWithApplication generates hanke based on application`() {
        val inputApplication = AlluDataFactory.cableReportWithoutHanke()

        val result = hankeService.generateHankeWithApplication(inputApplication, USER_NAME)

        with(result) {
            val application = applications.first()
            assertThat(hanke.hankeTunnus).isEqualTo(application.hankeTunnus)
            assertThat(hanke.nimi).isEqualTo(application.applicationData.name)
            assertThat(application.applicationData.name)
                .isEqualTo(inputApplication.applicationData.name)
            val hankePerustaja = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)?.perustaja
            assertThat(hankePerustaja?.nimi).isEqualTo("Teppo Testihenkilö")
            assertThat(hankePerustaja?.email).isEqualTo("teppo@example.test")
        }
    }

    @Test
    fun `generateHankeWithApplication when exception rolls back`() {
        // Use an intersecting geometry so that ApplicationService will throw an exception
        val inputApplication =
            CableReportWithoutHanke(
                ApplicationType.CABLE_REPORT,
                AlluDataFactory.createCableReportApplicationData(
                    areas =
                        listOf(
                            ApplicationArea(
                                "area",
                                "/fi/hel/haitaton/hanke/geometria/intersecting-polygon.json".asJsonResource()
                            )
                        )
                ),
            )

        assertThrows<ApplicationGeometryException> {
            hankeService.generateHankeWithApplication(inputApplication, USER_NAME)
        }

        assertEquals(0, hankeCount())
    }

    @Test
    fun `updateHanke creates new hankealue`() {
        val hanke = getATestHanke()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = hanke.alkuPvm,
                haittaLoppuPvm = hanke.loppuPvm,
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME,
                kaistaPituusHaitta = KaistajarjestelynPituus.KOLME,
                meluHaitta = Haitta13.KOLME,
                polyHaitta = Haitta13.KOLME,
                tarinaHaitta = Haitta13.KOLME,
            )
        val createdHanke = hankeService.createHanke(hanke)
        createdHanke.alueet.add(hankealue)

        val updatedHanke = hankeService.updateHanke(createdHanke)

        assertThat(updatedHanke.alueet).hasSize(2)
        val alue = updatedHanke.alueet[1]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(hanke.alkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(hanke.loppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
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
        val createdHanke = hankeService.createHanke(getATestHanke())
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
        val hanke = getATestHanke()
        val hankealue =
            HankealueFactory.create(
                id = null,
                haittaAlkuPvm = hanke.alkuPvm,
                haittaLoppuPvm = hanke.loppuPvm,
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME,
                kaistaPituusHaitta = KaistajarjestelynPituus.KOLME,
                meluHaitta = Haitta13.KOLME,
                polyHaitta = Haitta13.KOLME,
                tarinaHaitta = Haitta13.KOLME,
            )
        hanke.alueet.add(hankealue)
        val createdHanke = hankeService.createHanke(hanke)
        assertThat(createdHanke.alueet).hasSize(2)
        assertThat(hankealueCount()).isEqualTo(2)
        assertThat(geometriatCount()).isEqualTo(2)
        createdHanke.alueet.removeAt(0)

        val updatedHanke = hankeService.updateHanke(createdHanke)

        assertThat(updatedHanke.alueet).hasSize(1)
        val alue = updatedHanke.alueet[0]
        assertThat(alue.haittaAlkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(hanke.alkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.haittaLoppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
            .isEqualTo(hanke.loppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE))
        assertThat(alue.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KOLME)
        assertThat(alue.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.KOLME)
        assertThat(alue.meluHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.polyHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.tarinaHaitta).isEqualTo(Haitta13.KOLME)
        assertThat(alue.geometriat).isNotNull
        val hankeFromDb = hankeService.loadHanke(hanke.hankeTunnus!!)
        assertThat(hankeFromDb?.alueet).hasSize(1)
        assertThat(hankealueCount()).isEqualTo(1)
        assertThat(geometriatCount()).isEqualTo(1)
    }

    @Test
    fun `deleteHanke creates audit log entry for deleted hanke`() {
        val hanke = hankeService.createHanke(HankeFactory.create(id = null).withHankealue())
        val hankeWithTulos = hankeService.loadHanke(hanke.hankeTunnus!!)!!
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()

        hankeService.deleteHanke(hankeWithTulos, listOf(), "testUser")

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
        assertEquals(hanke.id?.toString(), event.target.id)
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
        val hanke =
            hankeService.createHanke(
                HankeFactory.create(id = null, hankeTunnus = null).withYhteystiedot { it.id = null }
            )
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()

        hankeService.deleteHanke(hanke, listOf(), "testUser")

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
        val hanke = hankeService.createHanke(HankeFactory.create(id = null))

        hankeService.deleteHanke(hanke, listOf(), USER_NAME)

        assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNull()
    }

    @Test
    fun `deleteHanke when hakemus is pending should delete hanke`() {
        val hakemusAlluId = 356
        val hanke = initHankeWithHakemus(hakemusAlluId)
        val hakemukset = hanke.hakemukset.map { it.toApplication() }
        every { cableReportService.getApplicationInformation(hakemusAlluId) } returns
            AlluDataFactory.createAlluApplicationResponse(status = ApplicationStatus.PENDING)
        justRun { cableReportService.cancel(hakemusAlluId) }
        every { cableReportService.sendSystemComment(hakemusAlluId, any()) } returns 1324

        hankeService.deleteHanke(hanke.toDomainObject(), hakemukset, USER_NAME)

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
        val hakemukset = hanke.hakemukset.map { it.toApplication() }
        every { cableReportService.getApplicationInformation(hakemusAlluId) } returns
            AlluDataFactory.createAlluApplicationResponse(status = ApplicationStatus.HANDLING)

        assertThrows<HankeAlluConflictException> {
            hankeService.deleteHanke(hanke.toDomainObject(), hakemukset, USER_NAME)
        }

        assertThat(hankeRepository.findByIdOrNull(hanke.id)).isNotNull
        verify { cableReportService.getApplicationInformation(hakemusAlluId) }
    }

    @Test
    fun `createHanke creates audit log entry for created hanke`() {
        TestUtils.addMockedRequestIp()

        val hanke = hankeService.createHanke(HankeFactory.create(id = null).withHankealue())

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
        assertEquals(hanke.id?.toString(), event.target.id)
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
        val hankeBeforeSave = HankeFactory.create(id = null)
        hankeBeforeSave.tyomaaKatuosoite = "Testikatu 1"
        hankeBeforeSave.tyomaaTyyppi = mutableSetOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)

        val hanke = hankeService.createHanke(hankeBeforeSave)

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
        assertEquals(hanke.id?.toString(), event.target.id)
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
        val hankeBeforeSave = HankeFactory.create(id = null)
        hankeBeforeSave.tyomaaKatuosoite = "Testikatu 1"
        hankeBeforeSave.tyomaaTyyppi = mutableSetOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        val hanke = hankeService.createHanke(hankeBeforeSave)
        val geometria: Geometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
                .asJsonResource<Geometriat>()
                .apply { id = 67 }
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
        assertEquals(hanke.id?.toString(), event.target.id)
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
        val hankeBeforeSave = HankeFactory.create(id = null).withHankealue()
        val hanke = hankeService.createHanke(hankeBeforeSave)
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
        assertEquals(hanke.id?.toString(), event.target.id)
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
                updatedHanke.id!!,
                updatedHanke.hankeTunnus!!,
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
        val hankeBeforeSave = HankeFactory.create(id = null)
        hankeBeforeSave.tyomaaKatuosoite = "Testikatu 1"
        hankeBeforeSave.tyomaaTyyppi = mutableSetOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        val hanke = hankeService.createHanke(hankeBeforeSave)
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
        val hanke = hankeService.createHanke(HankeFactory.create())

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
        val hanke = hankeRepository.save(HankeEntity(hankeTunnus = "HAI23-1"))
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

    private fun HankeEntity.toDomainObject(): Hanke =
        with(this) {
            Hanke(
                id,
                hankeTunnus,
                onYKTHanke,
                nimi,
                kuvaus,
                vaihe,
                suunnitteluVaihe,
                version,
                createdByUserId ?: "",
                createdAt?.atZone(TZ_UTC),
                modifiedByUserId,
                modifiedAt?.atZone(TZ_UTC),
                this.status
            )
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
                hanke.hankeTunnus ?: ""
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
            "/fi/hel/haitaton/hanke/logging/expectedHankeWithPolygon.json.mustache".getResourceAsText()
        )

    private fun expectedHankeLogObject(
        hanke: Hanke,
        alue: Hankealue? = null,
        geometriaVersion: Int = 0,
        hankeVersion: Int = 0,
        tormaystarkasteluTulos: Boolean = false,
        alkuPvm: String? = "${nextYear()}-02-20T00:00:00Z",
        loppuPvm: String? = "${nextYear()}-02-21T00:00:00Z",
    ): String {
        val templateData =
            TemplateData(
                hanke.id!!,
                hanke.hankeTunnus!!,
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
                "/fi/hel/haitaton/hanke/logging/expectedHankeWithPoints.json.mustache".getResourceAsText()
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
          "puhelinnumero":"010$i$i$i$i$i$i$i",
          "organisaatioNimi":"org$i",
          "osasto":"osasto$i",
          "rooli": "Isännöitsijä$i",
          "tyyppi": "YHTEISO",
          "alikontaktit": ${expectedYhteyshenkilot(i)}
        }"""

    private fun expectedYhteyshenkilot(i: Int) =
        """[{
            | "etunimi": "yhteys-etu$i",
            | "sukunimi": "yhteys-suku$i",
            | "email": "yhteys-email$i",
            | "puhelinnumero": "010$i$i$i$i$i$i$i"
            | }]""".trimMargin()

    /**
     * Fills a new Hanke domain object with test values and returns it. The audit and id/tunnus
     * fields are left at null. No Yhteystieto entries are created.
     */
    private fun getATestHanke(): Hanke =
        HankeFactory.create(
                id = null,
                hankeTunnus = null,
                vaihe = Vaihe.SUUNNITTELU,
                suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
                version = null,
                createdBy = null,
                createdAt = null,
            )
            .withHankealue()

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

    private fun hankeCount(): Int? =
        jdbcTemplate.queryForObject("SELECT count(*) from hanke", Int::class.java)
}
