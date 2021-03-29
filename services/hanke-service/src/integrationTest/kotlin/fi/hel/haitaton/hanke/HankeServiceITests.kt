package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.Action
import fi.hel.haitaton.hanke.logging.ChangeLogEntry
import fi.hel.haitaton.hanke.logging.PersonalDataAuditLogRepository
import fi.hel.haitaton.hanke.logging.PersonalDataChangeLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.Example
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
@WithMockUser(username = "test7358", roles = ["haitaton-user"])
class HankeServiceITests {

    // Must match the username of the mock user above
    private val USER_NAME = "test7358"

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer()
            .withExposedPorts(5433) // use non-default port
            .withPassword("test")
            .withUsername("test")

        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", container::getJdbcUrl)
            registry.add("spring.datasource.username", container::getUsername)
            registry.add("spring.datasource.password", container::getPassword)
            registry.add("spring.liquibase.url", container::getJdbcUrl)
            registry.add("spring.liquibase.user", container::getUsername)
            registry.add("spring.liquibase.password", container::getPassword)
        }
    }

    @Autowired
    private lateinit var hankeService: HankeService
    @Autowired
    private lateinit var personalDataAuditLogRepository: PersonalDataAuditLogRepository
    @Autowired
    private lateinit var personalDataChangeLogRepository: PersonalDataChangeLogRepository
    @Autowired
    private lateinit var hankeRepository: HankeRepository


    // Some tests also use and check loadHanke()'s return value because at one time the update action
    // was returning different set of data than loadHanke (bug), so it is a sort of regression test.

    @Test
    fun `create Hanke with full data set succeeds and returns a new domain object with the correct values`() {
        // Also tests that dates/times do not make timezone-related shifting on the roundtrip.
        // NOTE: times sent in and returned are expected to be in UTC ("Z" or +00:00 offset)

        // Setup Hanke with one Yhteystieto of each type:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        val yt3 = getATestYhteystieto(3)
        hanke.omistajat = arrayListOf(yt1)
        hanke.arvioijat = arrayListOf(yt2)
        hanke.toteuttajat = arrayListOf(yt3)

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
        val expectedDateAlku = datetimeAlku!!.truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
        val expectedDateLoppu = datetimeLoppu!!.truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z

        assertThat(returnedHanke.saveType).isEqualTo(SaveType.DRAFT)
        assertThat(returnedHanke.nimi).isEqualTo("testihanke yksi")
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
        assertThat(returnedHanke.kaistaHaitta).isEqualTo(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI)
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

        // Check state flag fields; all false in this case (because geometry has not been given):
        assertThat(returnedHanke.tilat.onGeometrioita).isFalse
        assertThat(returnedHanke.tilat.onKaikkiPakollisetLuontiTiedot).isFalse
        assertThat(returnedHanke.tilat.onTiedotLiikenneHaittaIndeksille).isFalse
        assertThat(returnedHanke.tilat.onLiikenneHaittaIndeksi).isFalse
        assertThat(returnedHanke.tilat.onViereisiaHankkeita).isFalse
        assertThat(returnedHanke.tilat.onAsiakasryhmia).isFalse
    }

    @Test
    fun `create Hanke with partial data set and update with full data set give correct state flags`() {
        // Setup Hanke (without any yhteystieto):
        val hanke: Hanke = getATestHanke("yksi", 1)
        // Also null one mandatory simple field:
        hanke.tyomaaKatuosoite = null

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(hanke)

        // Check the return object in general:
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull

        // Check certain flags for false state:
        assertThat(returnedHanke.tilat.onKaikkiPakollisetLuontiTiedot).isFalse
        assertThat(returnedHanke.tilat.onTiedotLiikenneHaittaIndeksille).isFalse

        // Fill the values and give proper save type:
        returnedHanke.tyomaaKatuosoite = "Testikatu 1 A 1"
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        val yt3 = getATestYhteystieto(3)
        returnedHanke.omistajat = arrayListOf(yt1)
        returnedHanke.arvioijat = arrayListOf(yt2)
        returnedHanke.toteuttajat = arrayListOf(yt3)
        returnedHanke.saveType = SaveType.SUBMIT

        // "Mocking" adding geometry stuff, by manually setting the geometry state flag to true
        // and calling the service to save flags:
        returnedHanke.tilat.onGeometrioita = true
        hankeService.updateHankeStateFlags(returnedHanke)

        // Call update
        val returnedHanke2 = hankeService.updateHanke(returnedHanke)

        // Check the return object in general:
        assertThat(returnedHanke2).isNotNull
        assertThat(returnedHanke2).isNotSameAs(returnedHanke)
        assertThat(returnedHanke2.id).isNotNull

        // Check those flags to be true now with full data (and the faked geometry) available:
        assertThat(returnedHanke2.tilat.onKaikkiPakollisetLuontiTiedot).isTrue
        assertThat(returnedHanke2.tilat.onTiedotLiikenneHaittaIndeksille).isTrue
    }

    @Test
    fun `test adding a new Yhteystieto to a group that already has one and to another group`() {
        // Also tests how update affects audit fields.

        // Setup Hanke with one Yhteystieto:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        hanke.omistajat = arrayListOf(yt1)

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
        val yt2 = getATestYhteystieto(2)
        val yt3 = getATestYhteystieto(3)
        returnedHanke.omistajat.add(yt2)
        returnedHanke.arvioijat = arrayListOf(yt3)

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
        // (Not checking all fields, just ensuring the code is not accidentally mixing whole entries).
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
        assertThat(returnedHanke3.omistajat[0].createdAt).isEqualTo(returnedHanke.omistajat[0].createdAt)
        assertThat(returnedHanke3.omistajat[0].createdBy).isEqualTo(returnedHanke.omistajat[0].createdBy)
        // The original omistajat-entry was not modified, so modifiedXx-fields must not get values:
        assertThat(returnedHanke3.omistajat[0].modifiedAt).isNull()
        assertThat(returnedHanke3.omistajat[0].modifiedBy).isNull()
    }

    @Test
    fun `test changing one existing Yhteystieto in a group with two`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        hanke.omistajat = arrayListOf(yt1, yt2)

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
        assertThat(returnedHanke3.omistajat[1].modifiedAt!!.toEpochSecond() - currentDatetime.toEpochSecond())
            .isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke3.omistajat[1].modifiedBy).isNotNull
        assertThat(returnedHanke3.omistajat[1].modifiedBy).isEqualTo(USER_NAME)
    }

    @Test
    fun `test that existing Yhteystieto that is sent fully empty gets removed`() {
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        hanke.omistajat = arrayListOf(yt1, yt2)

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

        // Check that one yhteystieto got removed, the first one remaining, and its fields not affected:
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
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(1)
        hanke.omistajat = arrayListOf(yt1)
        hanke.arvioijat = arrayListOf(yt2)

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull
        // Check and record the Yhteystieto ids, and that the id's are different:
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

        // Check that arvioija-yhteystieto got removed, the first one remaining, and its fields not affected:
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
        // NOTE: UI is not supposed to do that, but this situation came up during development/testing,
        // so it is a sort of regression test for the logic.

        // Setup Hanke with one Yhteystieto:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        hanke.omistajat = arrayListOf(yt1)

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
    fun `loadAllHankeBetweenDates returns Hanke when alkuPvm is inside the given time period`() {

        // Setup Hanke 1 that will not be returned in the result having too new alkuPvm and loppuPvm
        val hanke: Hanke = getATestHanke("yksi", 1)

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull

        // Setup Hanke which is the one we want to be returned as it starts within the wanted time period
        val hankeExpected: Hanke = getATestHanke("wanted", 2)

        // alkuPvm will be between the wanted search period
        hankeExpected.alkuPvm = ZonedDateTime.of(2000, 2, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        // Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()
        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull

        val criteria = HankeSearch(periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        // only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        // couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult[0].id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult[0].nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
    }

    @Test
    fun `loadAllHankeBetweenDates returns Hanke when both alkuPvm and loppuPvm are inside the period`() {

        // Setup Hanke 1 that will not be returned in the result having too new alkuPvm and loppuPvm
        val hanke: Hanke = getATestHanke("yksi", 1)

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull

        // Setup Hanke which is the one we want to be returned as it starts and ends within the wanted time period
        val hankeExpected: Hanke = getATestHanke("wanted", 2)

        // alkuPvm and loppuPvm will be between the wanted search period
        hankeExpected.alkuPvm = ZonedDateTime.of(2000, 2, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        hankeExpected.loppuPvm = ZonedDateTime.of(2000, 5, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        // Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull

        val criteria = HankeSearch(periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        // only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        // couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult[0].id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult[0].nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
    }

    @Test
    fun `loadAllHankeBetweenDates returns Hanke when only loppuPvm is inside the period`() {

        // Setup Hanke 1 that will not be returned in the result having too new alkuPvm and loppuPvm
        val hanke: Hanke = getATestHanke("yksi", 1)

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull()

        // Setup Hanke which is the one we want to be returned as it ends within the wanted time period
        val hankeExpected: Hanke = getATestHanke("wanted", 2)

        // ending is  inside the period but we put starting before it
        hankeExpected.alkuPvm = ZonedDateTime.of(1999, 5, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        hankeExpected.loppuPvm = ZonedDateTime.of(2000, 5, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        // Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull()

        val criteria = HankeSearch(periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        // only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        // couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult[0].id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult[0].nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
    }

    @Test
    fun `loadAllHankeBetweenDates returns hanke when alkuPvm is before the period and loppuPvm is after the period`() {

        // Setup Hanke 1 that will not be returned in the result having too new alkuPvm and loppuPvm
        val hanke: Hanke = getATestHanke("yksi", 1)

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull()

        // Setup Hanke which is the one we want to be returned as it is on going during the wanted time period
        val hankeExpected: Hanke = getATestHanke("wanted", 2)

        // alkuPvm will be before the period
        hankeExpected.alkuPvm = ZonedDateTime.of(1999, 2, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        // we put hanke to end after the period
        hankeExpected.loppuPvm = ZonedDateTime.of(2021, 5, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        // Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()
        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull()

        val criteria = HankeSearch(periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        // only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        // couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult[0].id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult[0].nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
    }

    @Test
    fun `test that loadAllHankeWithSavetype only returns hanke with correct saveType`() {

        // Setup Hanke 1 that will not be returned in the result having different saveType
        val hanke: Hanke = getATestHanke("yksi", 1)
        hanke.saveType = SaveType.DRAFT
        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)

        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull()

        // Setup Hanke which is the one we want to be returned as it has wanted saveType
        val hankeExpected: Hanke = getATestHanke("wanted", 2)
        hankeExpected.saveType = SaveType.SUBMIT
        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull

        val criteria = HankeSearch(saveType = SaveType.SUBMIT)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)

        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        // only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        // couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult[0].id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult[0].nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
    }

    @Test
    fun `test personal data logging`() {
        // Create hanke with two yhteystietos, save, check logs (creates in both logs, null old data)
        // Setup Hanke with two Yhteystietos in the same group:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        hanke.omistajat = arrayListOf(yt1, yt2)
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

        // Prepare example for searching entries by yhteystietoid:
        val sample1auditlog = Example.of(AuditLogEntry(yhteystietoId = ytid1))
        val sample1changelog = Example.of(ChangeLogEntry(yhteystietoId = ytid1))
        val sample2auditlog = Example.of(AuditLogEntry(yhteystietoId = ytid2))
        val sample2changelog = Example.of(ChangeLogEntry(yhteystietoId = ytid2))

        // Check logs...
        // Both logs must have 2 entries (two yhteystietos were created):
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(2)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(2)
        // Check that each yhteystieto has single entry in each log:
        var auditLogEntries1 = personalDataAuditLogRepository.findAll(sample1auditlog)
        var changeLogEntries1 = personalDataChangeLogRepository.findAll(sample1changelog)
        var auditLogEntries2 = personalDataAuditLogRepository.findAll(sample2auditlog)
        var changeLogEntries2 = personalDataChangeLogRepository.findAll(sample2changelog)
        assertThat(auditLogEntries1.size).isEqualTo(1)
        assertThat(changeLogEntries1.size).isEqualTo(1)
        assertThat(auditLogEntries2.size).isEqualTo(1)
        assertThat(changeLogEntries2.size).isEqualTo(1)
        // Check that each entry has correct data (action CREATE,
        // new data contains "suku1" or "suku2", and userid is that of the test user).
        assertThat(auditLogEntries1[0].action).isEqualTo(Action.CREATE)
        assertThat(auditLogEntries1[0].userId).isEqualTo(USER_NAME)
        assertThat(changeLogEntries1[0].action).isEqualTo(Action.CREATE)
        assertThat(changeLogEntries1[0].newData).contains("suku1")
        assertThat(auditLogEntries2[0].action).isEqualTo(Action.CREATE)
        assertThat(auditLogEntries2[0].userId).isEqualTo(USER_NAME)
        assertThat(changeLogEntries2[0].action).isEqualTo(Action.CREATE)
        assertThat(changeLogEntries2[0].oldData).isNull()
        assertThat(changeLogEntries2[0].newData).contains("suku2")
        assertThat(auditLogEntries1[0].failed).isFalse
        assertThat(auditLogEntries2[0].failed).isFalse
        assertThat(changeLogEntries1[0].failed).isFalse
        assertThat(changeLogEntries2[0].failed).isFalse

        // Update the other yhteystieto (one update in both logs, both datas exist and with correct values)
        // Change a value:
        returnedHanke.omistajat[1].sukunimi = "Som Et Hing"
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

        // Check logs...
        // Check that only 1 entry was added to each log (about the updated yhteystieto)
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(3)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(3)
        // Check that the second yhteystieto got a single entry in each log (and the other didn't)
        auditLogEntries1 = personalDataAuditLogRepository.findAll(sample1auditlog)
        changeLogEntries1 = personalDataChangeLogRepository.findAll(sample1changelog)
        auditLogEntries2 = personalDataAuditLogRepository.findAll(sample2auditlog)
        changeLogEntries2 = personalDataChangeLogRepository.findAll(sample2changelog)
        assertThat(auditLogEntries1.size).isEqualTo(1)
        assertThat(changeLogEntries1.size).isEqualTo(1)
        assertThat(auditLogEntries2.size).isEqualTo(2)
        assertThat(changeLogEntries2.size).isEqualTo(2)
        // Check that the new entry has correct data (action UPDATE,
        // old data contains "suku2", new data "Som Et Hing", and userid is that of the test user).
        assertThat(auditLogEntries2[1].action).isEqualTo(Action.UPDATE)
        assertThat(auditLogEntries2[1].userId).isEqualTo(USER_NAME)
        assertThat(changeLogEntries2[1].action).isEqualTo(Action.UPDATE)
        assertThat(changeLogEntries2[1].oldData).contains("suku2")
        assertThat(changeLogEntries2[1].newData).contains("Som Et Hing")
        assertThat(auditLogEntries2[1].failed).isFalse
        assertThat(changeLogEntries2[1].failed).isFalse

        // Delete the other yhteystieto (one update in both logs, null new data)
        returnedHanke2.omistajat[1].apply {
            etunimi = ""
            sukunimi = ""
            puhelinnumero = ""
            email = ""
            organisaatioNimi = ""
            osasto = ""
        }
        // Call update, get the returned object:
        val returnedHanke3 = hankeService.updateHanke(returnedHanke2)
        // Check that first yhteystieto remains, second one got removed:
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke3.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[0].sukunimi).isEqualTo("suku1")

        // Check logs...
        // Check that only 1 entry was added to each log (about the deleted yhteystieto)
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(4)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(4)
        // Check that the second yhteystieto got a single entry in each log (and the other didn't)
        auditLogEntries1 = personalDataAuditLogRepository.findAll(sample1auditlog)
        changeLogEntries1 = personalDataChangeLogRepository.findAll(sample1changelog)
        auditLogEntries2 = personalDataAuditLogRepository.findAll(sample2auditlog)
        changeLogEntries2 = personalDataChangeLogRepository.findAll(sample2changelog)
        assertThat(auditLogEntries1.size).isEqualTo(1)
        assertThat(changeLogEntries1.size).isEqualTo(1)
        assertThat(auditLogEntries2.size).isEqualTo(3)
        assertThat(changeLogEntries2.size).isEqualTo(3)
        // Check that the new entry has correct data (action DELETE,
        // old data contains "Som Et Hing", new data is null, and userid is that of the test user).
        assertThat(auditLogEntries2[2].action).isEqualTo(Action.DELETE)
        assertThat(auditLogEntries2[2].userId).isEqualTo(USER_NAME)
        assertThat(changeLogEntries2[2].action).isEqualTo(Action.DELETE)
        assertThat(changeLogEntries2[2].oldData).contains("Som Et Hing")
        assertThat(changeLogEntries2[2].newData).isNull()
        assertThat(auditLogEntries2[2].failed).isFalse
        assertThat(changeLogEntries2[2].failed).isFalse
    }

    @Test
    fun `test personal data processing restriction`() {
        // Setup Hanke with two Yhteystietos in different groups (will only manipulate the non-owner):
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        hanke.omistajat = arrayListOf(yt1)
        hanke.arvioijat = arrayListOf(yt2)
        // Call create, get the return object:
        val returnedHanke = hankeService.createHanke(hanke)
        // Check logs...
        // Both logs must have 2 entries (two yhteystietos were created):
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(2)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(2)

        // Get the non-owner yhteystieto,
        // and set the processing restriction (i.e. locked) -flag (must be done via entities):
        // (Fetching the yhteystieto is kind of clumsy as we don't have separate YhteystietoRepository.)
        val hankeId = returnedHanke.id
        var hankeEntity = hankeRepository.findById(hankeId!!).get()
        var yhteystietos = hankeEntity.listOfHankeYhteystieto
        var yhteystietoEntity = yhteystietos.filter { it.contactType == ContactType.ARVIOIJA }[0]
        val ytid = yhteystietoEntity.id
        yhteystietoEntity.dataLocked = true
        // (Not setting the info field, or adding audit log entry, since the idea
        // is to only test that the locking actually prevents processing.)
        // Saving the hanke will save the yhteystieto in it, too:
        hankeRepository.save(hankeEntity)

        // Try to update the yhteystieto; should fail and add audit and change log entries:
        val hankeWithLockedYT = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        hankeWithLockedYT!!.arvioijat[0].etunimi = "Muhaha-Evil-Change"

        assertThatExceptionOfType(HankeYhteystietoProcessingRestrictedException::class.java).isThrownBy {
            hankeService.updateHanke(hankeWithLockedYT)
        }
        // Check logs
        // The initial create has created two entries to each log, and now
        // the failed update should have added one more to each log.
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(3)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(3)
        val sampleauditlog = Example.of(AuditLogEntry(yhteystietoId = ytid))
        val samplechangelog = Example.of(ChangeLogEntry(yhteystietoId = ytid))
        var auditLogEntries = personalDataAuditLogRepository.findAll(sampleauditlog)
        var changeLogEntries = personalDataChangeLogRepository.findAll(samplechangelog)
        // For the second yhteystieto, 1 entry for the earlier creation, another for this failed update:
        assertThat(auditLogEntries.size).isEqualTo(2)
        assertThat(changeLogEntries.size).isEqualTo(2)
        assertThat(auditLogEntries[1].action).isEqualTo(Action.UPDATE)
        assertThat(auditLogEntries[1].userId).isEqualTo(USER_NAME)
        assertThat(changeLogEntries[1].action).isEqualTo(Action.UPDATE)
        assertThat(changeLogEntries[1].oldData).contains("suku2")
        assertThat(changeLogEntries[1].newData).contains("Muhaha-Evil-Change")
        assertThat(auditLogEntries[1].failed).isTrue
        assertThat(changeLogEntries[1].failed).isTrue

        // Try to delete the yhteystieto; should fail and add only audit log entry (nothing to change log):
        hankeWithLockedYT.arvioijat[0].apply {
            etunimi = ""
            sukunimi = ""
            puhelinnumero = ""
            email = ""
            organisaatioNimi = ""
            osasto = ""
        }
        assertThatExceptionOfType(HankeYhteystietoProcessingRestrictedException::class.java).isThrownBy {
            hankeService.updateHanke(hankeWithLockedYT)
        }
        // Check logs (should have one more entry in the audit log)
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(4)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(3)
        auditLogEntries = personalDataAuditLogRepository.findAll(sampleauditlog)
        changeLogEntries = personalDataChangeLogRepository.findAll(samplechangelog)
        // For the second yhteystieto, 1 more audit log entry for this failed deletion:
        assertThat(auditLogEntries.size).isEqualTo(3)
        assertThat(changeLogEntries.size).isEqualTo(2)
        assertThat(auditLogEntries[2].action).isEqualTo(Action.DELETE)
        assertThat(auditLogEntries[2].userId).isEqualTo(USER_NAME)
        assertThat(auditLogEntries[2].failed).isTrue

        // Check that both yhteystietos still exist and the values have not gotten changed:
        val returnedHankeAfterBlockedActions = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        val arvioijat = returnedHankeAfterBlockedActions!!.arvioijat
        assertThat(arvioijat).hasSize(1)
        assertThat(arvioijat[0].etunimi).isEqualTo("etu2")

        // Unset the processing restriction flag:
        hankeEntity = hankeRepository.findById(hankeId).get()
        yhteystietos = hankeEntity.listOfHankeYhteystieto
        yhteystietoEntity = yhteystietos.filter { it.contactType == ContactType.ARVIOIJA }[0]
        yhteystietoEntity.dataLocked = false
        hankeRepository.save(hankeEntity)

        // Updating the yhteystieto should now work:
        val hankeWithUnlockedYT = hankeService.loadHanke(returnedHanke.hankeTunnus!!)
        hankeWithUnlockedYT!!.arvioijat[0].etunimi = "Hopefully-Not-Evil-Change"
        val finalHanke = hankeService.updateHanke(hankeWithUnlockedYT)

        // Check that the change went through:
        assertThat(finalHanke.arvioijat[0].etunimi).isEqualTo("Hopefully-Not-Evil-Change")
        // Check logs (should have one more entry in both logs)
        assertThat(personalDataAuditLogRepository.count()).isEqualTo(5)
        assertThat(personalDataChangeLogRepository.count()).isEqualTo(4)
        auditLogEntries = personalDataAuditLogRepository.findAll(sampleauditlog)
        changeLogEntries = personalDataChangeLogRepository.findAll(samplechangelog)
        // For the second yhteystieto, 1 more entry in both logs:
        assertThat(auditLogEntries.size).isEqualTo(4)
        assertThat(changeLogEntries.size).isEqualTo(3)
    }


    /**
     * Just fills a new Hanke domain object with some crap (excluding any Yhteystieto entries) and returns it.
     * The audit and id/tunnus fields are left at null.
     */
    private fun getATestHanke(stringValue: String, intValue: Int): Hanke {
        // These time values should reveal possible timezone shifts, and not get affected by database time rounding.
        // (That is, if timezone handling causes even 1 hour shift one or the other, either one of these values
        // will flip over to previous/next day with the date-truncation effect done in the service.)
        val year = getCurrentTimeUTC().year + 1

        val dateAlku = ZonedDateTime.of(year, 2, 20, 23, 45, 56, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        val dateLoppu = ZonedDateTime.of(year, 2, 21, 0, 12, 34, 0, TZ_UTC)
            .truncatedTo(ChronoUnit.MILLIS)
        val hanke = Hanke(
            id = null,
            hankeTunnus = null,
            nimi = "testihanke $stringValue",
            kuvaus = "lorem ipsum dolor sit amet...",
            onYKTHanke = false,
            alkuPvm = dateAlku,
            loppuPvm = dateLoppu,
            vaihe = Vaihe.SUUNNITTELU,
            suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
            version = null,
            createdBy = null,
            createdAt = null,
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        )

        hanke.tyomaaKatuosoite = "Testikatu $intValue"
        hanke.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hanke.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        hanke.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hanke.haittaAlkuPvm = dateAlku
        hanke.haittaLoppuPvm = dateLoppu
        hanke.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
        hanke.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
        hanke.meluHaitta = Haitta13.YKSI
        hanke.polyHaitta = Haitta13.KAKSI
        hanke.tarinaHaitta = Haitta13.KOLME

        return hanke
    }

    /**
     * Returns a new Yhteystieto with values set to include the given integer value.
     * The audit and id fields are left null.
     */
    private fun getATestYhteystieto(intValue: Int): HankeYhteystieto {
        return HankeYhteystieto(
            null,
            "suku$intValue",
            "etu$intValue",
            "email$intValue",
            "010$intValue$intValue$intValue$intValue$intValue$intValue$intValue",
            intValue,
            "org$intValue",
            "osasto$intValue"
        )
    }
}
