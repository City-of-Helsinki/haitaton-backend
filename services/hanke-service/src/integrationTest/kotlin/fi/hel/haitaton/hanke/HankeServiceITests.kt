package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedArvioija
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedOmistajat
import fi.hel.haitaton.hanke.factory.HankeFactory.withHaitta
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.logging.AuditLogEntryEntity
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.test.TestUtils
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.byLessThan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
@WithMockUser(username = "test7358", roles = ["haitaton-user"])
class HankeServiceITests : DatabaseTest() {

    // Must match the username of the mock user above
    private val USER_NAME = "test7358"

    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var hankeGeometriatService: HankeGeometriatService
    @Autowired private lateinit var auditLogRepository: AuditLogRepository
    @Autowired private lateinit var hankeRepository: HankeRepository

    // Some tests also use and check loadHanke()'s return value because at one time the update
    // action was returning different set of data than loadHanke (bug), so it is a sort of
    // regression test.

    @Test
    fun `create Hanke with full data set succeeds and returns a new domain object with the correct values`() {
        // Also tests that dates/times do not make timezone-related shifting on the roundtrip.
        // NOTE: times sent in and returned are expected to be in UTC ("Z" or +00:00 offset)

        // Setup Hanke with one Yhteystieto of each type:
        // The yhteystiedot are not in DB yet, so their id should be null.
        val hanke: Hanke = getATestHanke().withYhteystiedot { it.id = null }

        val datetimeAlku = hanke.alkuPvm // nextyear.2.20 23:45:56Z
        val datetimeLoppu = hanke.loppuPvm // nextyear.2.21 0:12:34Z
        // For checking audit field datetimes (with some minutes of margin for test running delay):
        val currentDatetime = getCurrentTimeUTC()

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(hanke)

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

        assertThat(returnedHanke.saveType).isEqualTo(SaveType.DRAFT)
        assertThat(returnedHanke.nimi).isEqualTo("Hämeentien perusparannus ja katuvalot")
        assertThat(returnedHanke.kuvaus).isEqualTo("lorem ipsum dolor sit amet...")
        assertThat(returnedHanke.alkuPvm).isEqualTo(expectedDateAlku)
        assertThat(returnedHanke.loppuPvm).isEqualTo(expectedDateLoppu)
        assertThat(returnedHanke.vaihe).isEqualTo(Vaihe.SUUNNITTELU)
        assertThat(returnedHanke.suunnitteluVaihe).isEqualTo(SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS)

        assertThat(returnedHanke.tyomaaKatuosoite).isEqualTo("Testikatu 1")
        assertThat(returnedHanke.tyomaaTyyppi).contains(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        assertThat(returnedHanke.tyomaaKoko).isEqualTo(TyomaaKoko.LAAJA_TAI_USEA_KORTTELI)
        assertThat(returnedHanke.haittaAlkuPvm).isEqualTo(expectedDateAlku)
        assertThat(returnedHanke.haittaLoppuPvm).isEqualTo(expectedDateLoppu)
        assertThat(returnedHanke.kaistaHaitta)
            .isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI)
        assertThat(returnedHanke.kaistaPituusHaitta).isEqualTo(KaistajarjestelynPituus.NELJA)
        assertThat(returnedHanke.meluHaitta).isEqualTo(Haitta13.YKSI)
        assertThat(returnedHanke.polyHaitta).isEqualTo(Haitta13.KAKSI)
        assertThat(returnedHanke.tarinaHaitta).isEqualTo(Haitta13.KOLME)

        assertThat(returnedHanke.version).isZero
        assertThat(returnedHanke.createdAt).isNotNull
        assertThat(returnedHanke.createdAt!!.toEpochSecond() - currentDatetime.toEpochSecond())
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke.createdBy).isNotNull
        assertThat(returnedHanke.createdBy).isEqualTo(USER_NAME)
        assertThat(returnedHanke.modifiedAt).isNull()
        assertThat(returnedHanke.modifiedBy).isNull()

        val ryt1: HankeYhteystieto = returnedHanke.omistajat[0]
        val ryt2: HankeYhteystieto = returnedHanke.arvioijat[0]
        val ryt3: HankeYhteystieto = returnedHanke.toteuttajat[0]
        assertThat(ryt1).isNotNull
        assertThat(ryt2).isNotNull
        assertThat(ryt3).isNotNull
        // Check that fields have not somehow gone mixed between yhteystietos:
        assertThat(ryt1.sukunimi).isEqualTo("suku1")
        assertThat(ryt2.sukunimi).isEqualTo("suku2")
        assertThat(ryt3.sukunimi).isEqualTo("suku3")
        // Check that all fields got there and back (with just one of the Yhteystietos):
        assertThat(ryt1.etunimi).isEqualTo("etu1")
        assertThat(ryt1.email).isEqualTo("email1")
        assertThat(ryt1.puhelinnumero).isEqualTo("0101111111")
        assertThat(ryt1.organisaatioId).isEqualTo(1)
        assertThat(ryt1.organisaatioNimi).isEqualTo("org1")
        assertThat(ryt1.osasto).isEqualTo("osasto1")
        // Check all the fields generated by backend (id, audits):
        // NOTE: can not guarantee a specific id here, but the ids should be different to each other
        assertThat(ryt1.id).isNotNull
        val firstId = ryt1.id!!
        assertThat(ryt1.createdAt).isNotNull
        assertThat(ryt1.createdAt!!.toEpochSecond() - currentDatetime.toEpochSecond())
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(ryt1.createdBy).isNotNull
        assertThat(ryt1.createdBy).isEqualTo(USER_NAME)

        assertThat(ryt1.modifiedAt).isNull()
        assertThat(ryt1.modifiedBy).isNull()

        assertThat(ryt2.id).isNotEqualTo(firstId)
        assertThat(ryt3.id).isNotEqualTo(firstId)
        assertThat(ryt3.id).isNotEqualTo(ryt2.id)
    }

    @Test
    fun `create Hanke with partial data set and update with full data set give correct state flags`() {
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

        // Check certain flags for false state:
        assertThat(returnedHanke.onKaikkiPakollisetLuontiTiedot()).isFalse

        // Fill the values and give proper save type:
        returnedHanke.tyomaaKatuosoite = "Testikatu 1 A 1"
        returnedHanke.withYhteystiedot { it.id = null }
        returnedHanke.saveType = SaveType.SUBMIT

        // Call update
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)

        // Check the return object in general:
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check those flags to be true now with full data available:
        assertThat(returnedHanke2.onKaikkiPakollisetLuontiTiedot()).isTrue
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

        // Add a new Yhteystieto to the omistajat-group, and another to arvioijat-group:
        returnedHanke
            .withGeneratedOmistaja(2) { it.id = null }
            .withGeneratedArvioija(3) { it.id = null }

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
        assertThat(returnedHanke2.arvioijat).hasSize(1)
        // Check that the first Yhteystieto has not changed, and the two new ones are as expected:
        // (Not checking all fields, just ensuring the code is not accidentally mixing whole
        // entries).
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid)
        assertThat(returnedHanke2.omistajat[0].sukunimi).isEqualTo("suku1")
        assertThat(returnedHanke2.omistajat[1].sukunimi).isEqualTo("suku2")
        assertThat(returnedHanke2.arvioijat[0].sukunimi).isEqualTo("suku3")
        assertThat(returnedHanke2.omistajat[1].id).isNotEqualTo(ytid)
        assertThat(returnedHanke2.arvioijat[0].id).isNotEqualTo(ytid)
        assertThat(returnedHanke2.arvioijat[0].id).isNotEqualTo(returnedHanke2.omistajat[1].id)

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke has the same 3 Yhteystietos:
        assertThat(returnedHanke3.omistajat).hasSize(2)
        assertThat(returnedHanke3.arvioijat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid)
        assertThat(returnedHanke3.omistajat[0].sukunimi).isEqualTo("suku1")
        assertThat(returnedHanke3.omistajat[1].sukunimi).isEqualTo("suku2")
        assertThat(returnedHanke3.arvioijat[0].sukunimi).isEqualTo("suku3")
        assertThat(returnedHanke3.omistajat[1].id).isNotEqualTo(ytid)
        assertThat(returnedHanke3.arvioijat[0].id).isNotEqualTo(ytid)
        assertThat(returnedHanke3.arvioijat[0].id).isNotEqualTo(returnedHanke3.omistajat[1].id)

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
        assertThat(returnedHanke.omistajat[1].sukunimi).isEqualTo("suku2")

        // Change a value:
        returnedHanke.omistajat[1].sukunimi = "Som Et Hing"

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
        assertThat(returnedHanke2.omistajat[0].sukunimi).isEqualTo("suku1")
        assertThat(returnedHanke2.omistajat[1].sukunimi).isEqualTo("Som Et Hing")

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
        assertThat(returnedHanke3.omistajat[0].sukunimi).isEqualTo("suku1")
        assertThat(returnedHanke3.omistajat[1].sukunimi).isEqualTo("Som Et Hing")

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
        returnedHanke.omistajat[1].sukunimi = ""
        returnedHanke.omistajat[1].etunimi = ""
        returnedHanke.omistajat[1].email = ""
        returnedHanke.omistajat[1].puhelinnumero = ""
        returnedHanke.omistajat[1].organisaatioId = 0 // does not really matter, but...
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
        assertThat(returnedHanke2.omistajat[0].sukunimi).isEqualTo("suku1")

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
        assertThat(returnedHanke3.omistajat[0].sukunimi).isEqualTo("suku1")
    }

    @Test
    fun `test adding identical Yhteystietos in different groups and removing the other`() {
        // Setup Hanke with two identical Yhteystietos in different group:
        val hanke: Hanke =
            getATestHanke()
                .withGeneratedOmistaja(1) { it.id = null }
                .withGeneratedArvioija(1) { it.id = null }

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check and record the Yhteystieto ids, and that the ids are different:
        assertThat(returnedHanke.omistajat).hasSize(1)
        assertThat(returnedHanke.omistajat[0].id).isNotNull
        assertThat(returnedHanke.arvioijat).hasSize(1)
        assertThat(returnedHanke.arvioijat[0].id).isNotNull
        val ytid1 = returnedHanke.omistajat[0].id!!
        val ytid2 = returnedHanke.arvioijat[0].id!!
        assertThat(ytid1).isNotEqualTo(ytid2)

        // Remove the arvioija-yhteystieto:
        returnedHanke.arvioijat[0].sukunimi = ""
        returnedHanke.arvioijat[0].etunimi = ""
        returnedHanke.arvioijat[0].email = ""
        returnedHanke.arvioijat[0].puhelinnumero = ""
        returnedHanke.arvioijat[0].organisaatioId = 0 // does not really matter, but...
        returnedHanke.arvioijat[0].organisaatioNimi = ""
        returnedHanke.arvioijat[0].osasto = ""

        // Call update, get the returned object, make some general checks:
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(hanke)
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check that arvioija-yhteystieto got removed, the first one remaining, and its fields not
        // affected:
        assertThat(returnedHanke2.arvioijat).hasSize(0)
        assertThat(returnedHanke2.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].sukunimi).isEqualTo("suku1")

        // Use loadHanke and check it returns the same data:
        val returnedHanke3 = hankeService.loadHanke(returnedHanke2.hankeTunnus!!)
        // General checks (because using another API action)
        assertThat(returnedHanke3).isNotNull
        assertThat(returnedHanke3).isNotSameAs(returnedHanke)
        assertThat(returnedHanke3).isNotSameAs(returnedHanke2)
        assertThat(returnedHanke3!!.id).isNotNull

        // Check that the returned hanke has the same 2 Yhteystietos:
        assertThat(returnedHanke3.arvioijat).hasSize(0)
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[0].sukunimi).isEqualTo("suku1")
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
        assertThat(createdHanke.omistajat[1].sukunimi).isEqualTo("suku2")

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
        assertThat(auditLogEvents1[0].target.objectAfter).contains("suku1")

        assertThat(auditLogEvents2[0].operation).isEqualTo(Operation.CREATE)
        assertThat(auditLogEvents2[0].actor.userId).isEqualTo(USER_NAME)
        assertThat(auditLogEvents2[0].actor.role).isEqualTo(UserRole.USER)
        assertThat(auditLogEvents2[0].status).isEqualTo(Status.SUCCESS)
        assertThat(auditLogEvents2[0].failureDescription).isNull()
        assertThat(auditLogEvents2[0].target.type).isEqualTo(ObjectType.YHTEYSTIETO)
        assertThat(auditLogEvents2[0].target.objectBefore).isNull()
        assertThat(auditLogEvents2[0].target.objectAfter).contains("suku2")

        // Update one yhteystieto. This should create one update row in the log. ObjectBefore and
        // objectAfter fields should exist and have correct values.
        // Change a value:
        createdHanke.omistajat[1].sukunimi = "Som Et Hing"
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
        assertThat(hankeAfterUpdate.omistajat[0].sukunimi).isEqualTo("suku1")
        assertThat(hankeAfterUpdate.omistajat[1].sukunimi).isEqualTo("Som Et Hing")

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
        assertThat(auditLogEvents2[1].target.objectBefore).contains("suku2")
        assertThat(auditLogEvents2[1].target.objectAfter).contains("Som Et Hing")

        // Delete the other yhteystieto. This should create one update in log, with null
        // objectAfter.
        hankeAfterUpdate.omistajat[1].apply {
            etunimi = ""
            sukunimi = ""
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
        assertThat(hankeAfterDelete.omistajat[0].sukunimi).isEqualTo("suku1")

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
        assertThat(auditLogEvents2[2].target.objectBefore).contains("Som Et Hing")
        assertThat(auditLogEvents2[2].target.objectAfter).isNull()
    }

    @Test
    fun `test personal data processing restriction`() {
        // Setup Hanke with two Yhteystietos in different groups. The test will only manipulate the
        // arvioija.
        val hanke: Hanke =
            getATestHanke()
                .withGeneratedOmistaja(1) { it.id = null }
                .withGeneratedArvioija(2) { it.id = null }
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
        var arvioijaEntity = yhteystietos.filter { it.contactType == ContactType.ARVIOIJA }[0]
        val arvioijaId = arvioijaEntity.id.toString()
        arvioijaEntity.dataLocked = true
        // Not setting the info field, or adding audit log entry, since the idea is to only test
        // that the locking actually prevents processing.
        // Saving the hanke will save the yhteystieto in it, too:
        hankeRepository.save(hankeEntity)

        // Try to update the yhteystieto. It should fail and add a new log entry.
        val hankeWithLockedYT = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        hankeWithLockedYT!!.arvioijat[0].etunimi = "Muhaha-Evil-Change"

        assertThatExceptionOfType(HankeYhteystietoProcessingRestrictedException::class.java)
            .isThrownBy { hankeService.updateHanke(hankeWithLockedYT) }
        // The initial create has created two entries to the log, and now the failed update should
        // have added one more.
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(3)
        var auditLogEvents =
            auditLogRepository
                .findByType(ObjectType.YHTEYSTIETO)
                .map { it.message.auditEvent }
                .filter { it.target.id == arvioijaId }
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
        assertThat(auditLogEvents[1].target.objectBefore).contains("suku2")
        assertThat(auditLogEvents[1].target.objectAfter).contains("Muhaha-Evil-Change")

        // Try to delete the yhteystieto. It should fail and add a new log entry.
        hankeWithLockedYT.arvioijat[0].apply {
            etunimi = ""
            sukunimi = ""
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
                .filter { it.target.id == arvioijaId }
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
        assertThat(auditLogEvents[2].target.objectBefore).contains("suku2")
        assertThat(auditLogEvents[2].target.objectAfter).isNull()

        // Check that both yhteystietos still exist and the values have not gotten changed.
        val returnedHankeAfterBlockedActions = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        val arvioijat = returnedHankeAfterBlockedActions!!.arvioijat
        assertThat(arvioijat).hasSize(1)
        assertThat(arvioijat[0].etunimi).isEqualTo("etu2")

        // Unset the processing restriction flag:
        hankeEntity = hankeRepository.findById(hankeId).get()
        yhteystietos = hankeEntity.listOfHankeYhteystieto
        arvioijaEntity = yhteystietos.filter { it.contactType == ContactType.ARVIOIJA }[0]
        arvioijaEntity.dataLocked = false
        hankeRepository.save(hankeEntity)

        // Updating the yhteystieto should now work:
        val hankeWithUnlockedYT = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        hankeWithUnlockedYT!!.arvioijat[0].etunimi = "Hopefully-Not-Evil-Change"
        val finalHanke = hankeService.updateHanke(hankeWithUnlockedYT)

        // Check that the change went through:
        assertThat(finalHanke.arvioijat[0].etunimi).isEqualTo("Hopefully-Not-Evil-Change")
        // There should be one more entry in the log.
        assertThat(auditLogRepository.countByType(ObjectType.YHTEYSTIETO)).isEqualTo(5)
        auditLogEvents =
            auditLogRepository
                .findByType(ObjectType.YHTEYSTIETO)
                .map { it.message.auditEvent }
                .filter { it.target.id == arvioijaId }
        // For the second yhteystieto, there should be one more entry in the log:
        assertThat(auditLogEvents).hasSize(4)
    }

    @Test
    fun `deleteHanke creates audit log entries for deleted hanke`() {
        val hanke = hankeService.createHanke(HankeFactory.create(id = null).withHaitta())
        val geometria: HankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()
        val geometriat = hankeGeometriatService.saveGeometriat(hanke, geometria)
        val hankeWithGeometria = hanke.apply { this.geometriat = geometriat }
        // Update needed for generating TormaystarkasteluTulos
        hankeService.updateHanke(hankeWithGeometria)
        val hankeWithTulos =
            hankeService.loadHanke(hanke.hankeTunnus!!)!!.apply { this.geometriat = geometriat }
        auditLogRepository.deleteAll()
        assertEquals(0, auditLogRepository.count())
        TestUtils.addMockedRequestIp()

        hankeService.deleteHanke(hankeWithTulos, "testUser")

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
        assertEquals("127.0.0.1", event.actor.ipAddress)
        assertEquals(hanke.id?.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        assertNull(event.target.objectAfter)
        val expectedObject =
            expectedHankeLogObject(
                hankeWithTulos.id,
                hankeWithTulos.hankeTunnus,
                1,
                """{
               |"perusIndeksi":1.4,
               |"pyorailyIndeksi":1.0,
               |"joukkoliikenneIndeksi":1.0,
               |"liikennehaittaIndeksi":{"indeksi":1.4,"tyyppi":"PERUSINDEKSI"}
               |}""".trimMargin()
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

        hankeService.deleteHanke(hanke, "testUser")

        val logs = auditLogRepository.findByType(ObjectType.YHTEYSTIETO)
        assertEquals(3, logs.size)
        val deleteLogs = logs.filter { it.message.auditEvent.operation == Operation.DELETE }
        assertThat(deleteLogs).hasSize(3).allSatisfy { log ->
            assertFalse(log.isSent)
            assertThat(log.createdAt).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
            val event = log.message.auditEvent
            assertThat(event.dateTime).isCloseToUtcNow(byLessThan(1, ChronoUnit.MINUTES))
            assertEquals(Status.SUCCESS, event.status)
            assertNull(event.failureDescription)
            assertEquals("1", event.appVersion)
            assertEquals("testUser", event.actor.userId)
            assertEquals(UserRole.USER, event.actor.role)
            assertEquals("127.0.0.1", event.actor.ipAddress)
        }
        val omistajaId = hanke.omistajat[0].id!!
        val omistajaEvent = deleteLogs.findByTargetId(omistajaId).message.auditEvent
        JSONAssert.assertEquals(
            expectedYhteystietoDeleteLogObject(omistajaId, 1),
            omistajaEvent.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val arvioijaId = hanke.arvioijat[0].id!!
        val arvioijaEvent = deleteLogs.findByTargetId(arvioijaId).message.auditEvent
        JSONAssert.assertEquals(
            expectedYhteystietoDeleteLogObject(arvioijaId, 2),
            arvioijaEvent.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
        val toteuttajaId = hanke.toteuttajat[0].id!!
        val toteuttajaEvent = deleteLogs.findByTargetId(toteuttajaId).message.auditEvent
        JSONAssert.assertEquals(
            expectedYhteystietoDeleteLogObject(toteuttajaId, 3),
            toteuttajaEvent.target.objectBefore,
            JSONCompareMode.NON_EXTENSIBLE
        )
    }

    @Test
    fun `createHanke creates audit log entry for created hanke`() {
        TestUtils.addMockedRequestIp()

        val hanke = hankeService.createHanke(HankeFactory.create(id = null).withHaitta())

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
        assertEquals("test7358", event.actor.userId)
        assertEquals(UserRole.USER, event.actor.role)
        assertEquals("127.0.0.1", event.actor.ipAddress)
        assertEquals(hanke.id?.toString(), event.target.id)
        assertEquals(ObjectType.HANKE, event.target.type)
        assertNull(event.target.objectBefore)
        val expectedObject = expectedHankeLogObject(hanke.id, hanke.hankeTunnus, 0, null)
        assertEquals(
            OBJECT_MAPPER.readTree(expectedObject),
            OBJECT_MAPPER.readTree(event.target.objectAfter),
        )
    }

    private fun expectedHankeLogObject(
        id: Int?,
        hankeTunnus: String?,
        version: Int,
        tormaystarkasteluTulos: String?
    ) =
        """{"id":$id,
               |"hankeTunnus":"$hankeTunnus",
               |"onYKTHanke":true,
               |"nimi":"Hämeentien perusparannus ja katuvalot",
               |"kuvaus":"lorem ipsum dolor sit amet...",
               |"alkuPvm":"2023-02-20T00:00:00Z",
               |"loppuPvm":"2023-02-21T00:00:00Z",
               |"vaihe":"OHJELMOINTI",
               |"suunnitteluVaihe":null,
               |"version":$version,
               |"tyomaaKatuosoite":"Testikatu 1",
               |"tyomaaTyyppi":["VESI", "MUU"],
               |"tyomaaKoko":"LAAJA_TAI_USEA_KORTTELI",
               |"haittaAlkuPvm":"2023-02-20T00:00:00Z",
               |"haittaLoppuPvm":"2023-02-21T00:00:00Z",
               |"kaistaHaitta":"KAKSI",
               |"kaistaPituusHaitta":"NELJA",
               |"meluHaitta":"YKSI",
               |"polyHaitta":"KAKSI",
               |"tarinaHaitta":"KOLME",
               |"tormaystarkasteluTulos":$tormaystarkasteluTulos
               |}""".trimMargin()

    private fun List<AuditLogEntryEntity>.findByTargetId(id: Int): AuditLogEntryEntity =
        this.filter { it.message.auditEvent.target.id == id.toString() }[0]

    /**
     * Creates the logged object with the same content as
     * [fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory.createDifferentiated].
     */
    private fun expectedYhteystietoDeleteLogObject(id: Int?, i: Int) =
        """{
           |"id":$id,
           |"sukunimi":"suku$i",
           |"etunimi":"etu$i",
           |"email":"email$i",
           |"puhelinnumero":"010$i$i$i$i$i$i$i",
           |"organisaatioId":$i,
           |"organisaatioNimi":"org$i",
           |"osasto":"osasto$i"
           |}""".trimMargin()

    /**
     * Fills a new Hanke domain object with test values and returns it. The audit and id/tunnus
     * fields are left at null. No Yhteystieto entries are created.
     */
    private fun getATestHanke(): Hanke =
        HankeFactory.create(
                id = null,
                hankeTunnus = null,
                alkuPvm = DateFactory.getStartDatetime(),
                loppuPvm = DateFactory.getEndDatetime(),
                vaihe = Vaihe.SUUNNITTELU,
                suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
                version = null,
                createdBy = null,
                createdAt = null,
            )
            .withHaitta()

    /**
     * Find all audit logs for a specific object type. Getting all and filtering would obviously not
     * be acceptable in production, but in tests we usually have a very limited number of entities
     * at any one test.
     *
     * This way we don't have to add a new repository method only used in tests.
     */
    fun AuditLogRepository.findByType(type: ObjectType) =
        this.findAll().filter { it.message.auditEvent.target.type == type }

    fun AuditLogRepository.countByType(type: ObjectType) =
        this.findAll().count { it.message.auditEvent.target.type == type }
}
