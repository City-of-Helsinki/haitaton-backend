package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles


// TODO: works with the springboottest, but not with the datajpatest... WHY ?

@ActiveProfiles("default")
@SpringBootTest
//@DataJpaTest(properties = ["spring.liquibase.enabled=false"])
class HankeServiceITests {

    @Autowired
    private lateinit var hankeService: HankeService

    @Test
    fun `create Hanke with full data set succeeds and returns new domain object`() {
        val hanke: Hanke = getATestHanke("yksi", 1)

        val returnedHanke = hankeService.createHanke(hanke)

        assertThat(returnedHanke).isNotNull

        assertThat(returnedHanke.nimi).isEqualTo("testihanke yksi")
        // TODO: more checks

    }

    // TODO: more tests (mostly about correct handling of data that goes to other tables)

    /**
     * Just fills a new Hanke domain object with some crap and returns it.
     * The audit and id/tunnus fields are left at null.
     */
    private fun getATestHanke(stringValue: String, intValue: Int): Hanke {
        val date = getCurrentTimeUTC()
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

}
