package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Import(HankeServiceImpl::class)
@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class HankeServiceITests {

    @Autowired
    private lateinit var hankeService: HankeService

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
        assertThat(returnedHanke.kaistaHaitta).isEqualTo(Haitta04.KAKSI)
        assertThat(returnedHanke.kaistaPituusHaitta).isEqualTo(Haitta04.NELJA)
        assertThat(returnedHanke.meluHaitta).isEqualTo(Haitta13.YKSI)
        assertThat(returnedHanke.polyHaitta).isEqualTo(Haitta13.KAKSI)
        assertThat(returnedHanke.tarinaHaitta).isEqualTo(Haitta13.KOLME)

        assertThat(returnedHanke.version).isZero
        assertThat(returnedHanke.createdAt).isNotNull
        assertThat(returnedHanke.createdAt!!.toEpochSecond() - currentDatetime.toEpochSecond()).isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke.createdBy).isNotNull // TODO: once getting users, this might be nice to check a match
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
        // NOTE: can not quarantee a specific id here, but the ids should be different to each other
        assertThat(ryt1.id).isNotNull
        val firstId = ryt1.id!!
        assertThat(ryt1.createdAt).isNotNull
        assertThat(ryt1.createdAt!!.toEpochSecond() - currentDatetime.toEpochSecond()).isBetween(-600, 600) // +/-10 minutes
        assertThat(ryt1.createdBy).isNotNull // TODO: once getting users, this might be nice to check a match
        assertThat(ryt1.modifiedAt).isNull()
        assertThat(ryt1.modifiedBy).isNull()

        assertThat(ryt2.id).isNotEqualTo(firstId)
        assertThat(ryt3.id).isNotEqualTo(firstId)
        assertThat(ryt3.id).isNotEqualTo(ryt2.id)
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
        assertThat(returnedHanke2.modifiedAt!!.toEpochSecond() - currentDatetime.toEpochSecond()).isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke2.modifiedBy).isNotNull // TODO: once getting users, this might be nice to check a match

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
        assertThat(returnedHanke3.id).isNotNull

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

        // A small side-check here for audit fields handling on update:
        assertThat(returnedHanke2.omistajat[0].createdAt).isEqualTo(returnedHanke.omistajat[0].createdAt)
        assertThat(returnedHanke2.omistajat[0].createdBy).isEqualTo(returnedHanke.omistajat[0].createdBy)
        assertThat(returnedHanke2.omistajat[0].modifiedAt).isNotNull
        assertThat(returnedHanke2.omistajat[0].modifiedAt!!.toEpochSecond() - currentDatetime.toEpochSecond()).isBetween(-600, 600) // +/-10 minutes
        assertThat(returnedHanke2.omistajat[0].modifiedBy).isNotNull // TODO: once getting users, this might be nice to check a match
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
        assertThat(returnedHanke3.id).isNotNull

        // Check that the returned hanke has the same 3 Yhteystietos:
        assertThat(returnedHanke3.omistajat).hasSize(2)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke3.omistajat[1].id).isEqualTo(ytid2)
        assertThat(returnedHanke3.omistajat[0].sukunimi).isEqualTo("suku1")
        assertThat(returnedHanke3.omistajat[1].sukunimi).isEqualTo("Som Et Hing")
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
        assertThat(returnedHanke3.id).isNotNull

        // Check that the returned hanke has the same 3 Yhteystietos:
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].sukunimi).isEqualTo("suku1")
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
        assertThat(returnedHanke3.id).isNotNull

        // Check that the returned hanke has the same 3 Yhteystietos:
        assertThat(returnedHanke2.arvioijat).hasSize(0)
        assertThat(returnedHanke3.omistajat).hasSize(1)
        assertThat(returnedHanke2.omistajat[0].id).isEqualTo(ytid1)
        assertThat(returnedHanke2.omistajat[0].sukunimi).isEqualTo("suku1")
    }


    @Test
    fun `test that sending the same Yhteystieto twice without id does not create duplicates`() {
        // Old version of the Yhteystieto should get removed, id increases in response, get-operation returns the new one.
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
        assertThat(returnedHanke3.id).isNotNull

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

        //alkuPvm will be between the wanted search period
        hankeExpected.alkuPvm = ZonedDateTime.of(2000, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        //Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()
        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull

        val criteria = HankeSearch (periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        //only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        //couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult.get(0).id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult.get(0).nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
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

        //alkuPvm and loppuPvm will be between the wanted search period
        hankeExpected.alkuPvm = ZonedDateTime.of(2000, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        hankeExpected.loppuPvm = ZonedDateTime.of(2000, 5, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        //Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull


        val criteria = HankeSearch (periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        //only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        //couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult.get(0).id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult.get(0).nimi).isEqualTo(returnedHankeWithWantedDate.nimi)

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

        //ending is  inside the period but we put starting before it
        hankeExpected.alkuPvm = ZonedDateTime.of(1999, 5, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        hankeExpected.loppuPvm = ZonedDateTime.of(2000, 5, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        //Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull()

        val criteria = HankeSearch (periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        //only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        //couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult.get(0).id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult.get(0).nimi).isEqualTo(returnedHankeWithWantedDate.nimi)
    }

    @Test
    fun `test that loadAllHankeBetweenDates returns hanke when alkuPvm is before the period and loppuPvm is after the period`() {

        // Setup Hanke 1 that will not be returned in the result having too new alkuPvm and loppuPvm
        val hanke: Hanke = getATestHanke("yksi", 1)

        // Call create, get the return object, and make some general checks:
        val returnedHanke = hankeService.createHanke(hanke)
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull()

        // Setup Hanke which is the one we want to be returned as it is on going during the wanted time period
        val hankeExpected: Hanke = getATestHanke("wanted", 2)

        //alkuPvm will be before the period
        hankeExpected.alkuPvm = ZonedDateTime.of(1999, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        //we put hanke to end after the period
        hankeExpected.loppuPvm = ZonedDateTime.of(2021, 5, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        //Period will be 1.1.2000-31.12.2000
        val periodStart = ZonedDateTime.of(2000, 1, 1, 1, 45, 56, 0, TZ_UTC).toLocalDate()
        val periodEnd = ZonedDateTime.of(2000, 12, 31, 1, 45, 56, 0, TZ_UTC).toLocalDate()

        // Call create, get the return object, and make some general checks:
        val returnedHankeWithWantedDate = hankeService.createHanke(hankeExpected)
        assertThat(returnedHankeWithWantedDate).isNotNull
        assertThat(returnedHankeWithWantedDate).isNotSameAs(hanke)
        assertThat(returnedHankeWithWantedDate.id).isNotNull()

        val criteria = HankeSearch (periodStart, periodEnd)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)
        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        //only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        //couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult.get(0).id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult.get(0).nimi).isEqualTo(returnedHankeWithWantedDate.nimi)

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
        assertThat(returnedHankeWithWantedDate.id).isNotNull()


        val criteria = HankeSearch (saveType = SaveType.SUBMIT)
        // Use loadHanke and check it also returns only one entry
        val returnedHankeResult = hankeService.loadAllHanke(criteria)

        // General checks (because using another API action)
        assertThat(returnedHankeResult).isNotNull
        //only one of the added hanke is between time period and should be returned
        assertThat(returnedHankeResult.size).isEqualTo(1)
        //couple of checks to make sure we got the wanted
        assertThat(returnedHankeResult.get(0).id).isEqualTo(returnedHankeWithWantedDate.id)
        assertThat(returnedHankeResult.get(0).nimi).isEqualTo(returnedHankeWithWantedDate.nimi)

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
        val hanke = Hanke(id = null, hankeTunnus = null, nimi = "testihanke $stringValue", kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = dateAlku, loppuPvm = dateLoppu, vaihe = Vaihe.SUUNNITTELU, suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
                version = null, createdBy = null, createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        hanke.tyomaaKatuosoite = "Testikatu $intValue"
        hanke.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hanke.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        hanke.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hanke.haittaAlkuPvm = dateAlku
        hanke.haittaLoppuPvm = dateLoppu
        hanke.kaistaHaitta = Haitta04.KAKSI
        hanke.kaistaPituusHaitta = Haitta04.NELJA
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
        return HankeYhteystieto(null,
                "suku$intValue", "etu$intValue", "email$intValue",
                "010$intValue$intValue$intValue$intValue$intValue$intValue$intValue",
                intValue, "org$intValue", "osasto$intValue")
    }

}
