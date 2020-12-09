package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit


// TODO: works with the springboottest, but not with the datajpatest... WHY ?

@ActiveProfiles("default")
@SpringBootTest
//@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class HankeServiceITests {

    @Autowired
    private lateinit var hankeService: HankeService

    @Test
    fun `create Hanke with full data set succeeds and returns a new domain object with the correct values`() {
        // Setup Hanke with one Yhteystieto of each type:
        val hanke: Hanke = getATestHanke("yksi", 1)
        val yt1 = getATestYhteystieto(1)
        val yt2 = getATestYhteystieto(2)
        val yt3 = getATestYhteystieto(3)
        hanke.omistajat = arrayListOf(yt1)
        hanke.arvioijat = arrayListOf(yt2)
        hanke.toteuttajat = arrayListOf(yt3)
        val datetime = hanke.alkuPvm
println("aika $datetime")

        // Call create and get the return object:
        val returnedHanke = hankeService.createHanke(hanke)

        // Check the return object in general:
        assertThat(returnedHanke).isNotNull
        assertThat(returnedHanke).isNotSameAs(hanke)
        assertThat(returnedHanke.id).isNotNull()

        // Check the fields:
        // Note, "pvm" values should have become truncated to begin of the day
        val date = datetime!!.truncatedTo(ChronoUnit.DAYS)
println("päivä $date")
println("alkuPvm $returnedHanke.alkuPvm")
        assertThat(returnedHanke.saveType).isEqualTo(SaveType.DRAFT)
        assertThat(returnedHanke.nimi).isEqualTo("testihanke yksi")
        assertThat(returnedHanke.kuvaus).isEqualTo("lorem ipsum dolor sit amet...")
        assertThat(returnedHanke.alkuPvm).isEqualTo(date)
        assertThat(returnedHanke.loppuPvm).isEqualTo(date)
        assertThat(returnedHanke.vaihe).isEqualTo(Vaihe.SUUNNITTELU)
        assertThat(returnedHanke.suunnitteluVaihe).isEqualTo(SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS)

        assertThat(returnedHanke.tyomaaKatuosoite).isEqualTo("Testikatu 1")
        assertThat(returnedHanke.tyomaaTyyppi).contains(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU)
        assertThat(returnedHanke.tyomaaKoko).isEqualTo(TyomaaKoko.LAAJA_TAI_USEA_KORTTELI)
        assertThat(returnedHanke.haittaAlkuPvm).isEqualTo(date)
        assertThat(returnedHanke.haittaLoppuPvm).isEqualTo(date)
        assertThat(returnedHanke.kaistaHaitta).isEqualTo(Haitta04.KAKSI)
        assertThat(returnedHanke.kaistaPituusHaitta).isEqualTo(Haitta04.NELJA)
        assertThat(returnedHanke.meluHaitta).isEqualTo(Haitta13.YKSI)
        assertThat(returnedHanke.polyHaitta).isEqualTo(Haitta13.KAKSI)
        assertThat(returnedHanke.tarinaHaitta).isEqualTo(Haitta13.KOLME)

        assertThat(returnedHanke.version).isZero()
        assertThat(returnedHanke.createdAt).isNotNull()
        assertThat(returnedHanke.createdBy).isNotNull()
        assertThat(returnedHanke.modifiedAt).isNull()
        assertThat(returnedHanke.modifiedBy).isNull()


        // TODO: Yhteystieto fields

    }

    // TODO: more tests (mostly about correct handling of data that goes to other tables)
    //   yhteystietojen lisäys, muutos, poisto

    /**
     * Just fills a new Hanke domain object with some crap (excluding any Yhteystieto entries) and returns it.
     * The audit and id/tunnus fields are left at null.
     */
    private fun getATestHanke(stringValue: String, intValue: Int): Hanke {
        // Truncating to milliseconds so that database truncation does not affect testing
        val date = ZonedDateTime.of(2020, 2, 20, 20, 20, 20, 20, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS)
        val hanke = Hanke(id = null, hankeTunnus = null, nimi = "testihanke $stringValue", kuvaus = "lorem ipsum dolor sit amet...",
                onYKTHanke = false, alkuPvm = date, loppuPvm = date, vaihe = Vaihe.SUUNNITTELU, suunnitteluVaihe = SuunnitteluVaihe.RAKENNUS_TAI_TOTEUTUS,
                version = null, createdBy = null, createdAt = null, modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        hanke.tyomaaKatuosoite = "Testikatu $intValue"
        hanke.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
        hanke.tyomaaTyyppi.add(TyomaaTyyppi.MUU)
        hanke.tyomaaKoko = TyomaaKoko.LAAJA_TAI_USEA_KORTTELI
        hanke.haittaAlkuPvm = date
        hanke.haittaLoppuPvm = date
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
