package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.TormaysAnalyysiException
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TormaystarkasteluLuokitteluServiceImplTest {

    private val tormaysPaikkaDao: TormaystarkasteluDao = Mockito.mock(TormaystarkasteluDao::class.java)

    @BeforeEach
    fun setUp() {
        Mockito.clearInvocations(tormaysPaikkaDao)
    }

    @ParameterizedTest(name = "{0} days from 2021-03-02 will give 'haitta-ajan kesto' classification of {1}")
    @CsvSource("0,1", "12,1", "13,3", "89,3", "90,5", "180,5")
    fun haittaAjanKesto(days: Long, classficationValue: Int) {
        val hanke = Hanke(1, "HAI21-1").apply {
            haittaAlkuPvm = ZonedDateTime.of(2021, 3, 2, 0, 0, 0, 0, TZ_UTC)
            haittaLoppuPvm = haittaAlkuPvm!!.plusDays(days)
        }
        val rajaArvot = LuokitteluRajaArvot()

        val haittaAjanKesto = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).haittaAjanKesto(hanke, rajaArvot)

        assertThat(haittaAjanKesto.arvo).isEqualTo(classficationValue)
    }

    @ParameterizedTest(name = "Lane hindrance of {0} means classification of {1}")
    @CsvSource(
        "EI_VAIKUTA,1",
        "VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA,2",
        "VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA,3",
        "VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA,4",
        "VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA,5"
    )
    fun todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin(
        kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin,
        arvo: Int
    ) {
        val hanke = Hanke(1, "HAI21-1").apply {
            this.kaistaHaitta = kaistaHaitta
        }

        val todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin =
            TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao)
                .todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin(hanke)

        assertThat(todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin.arvo).isEqualTo(arvo)
    }

    @ParameterizedTest(name = "Lane organization length of {0} means classification of {1}")
    @CsvSource(
        "EI_TARVITA,1",
        "ENINTAAN_10M,2",
        "ALKAEN_11M_PAATTYEN_100M,3",
        "ALKAEN_101M_PAATTYEN_500M,4",
        "YLI_500M,5"
    )
    fun kaistajarjestelynPituus(
        kaistaPituusHaitta: KaistajarjestelynPituus,
        arvo: Int
    ) {
        val hanke = Hanke(1, "HAI21-1").apply {
            this.kaistaPituusHaitta = kaistaPituusHaitta
        }

        val kaistajarjestelynPituus =
            TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).kaistajarjestelynPituus(hanke)

        assertThat(kaistajarjestelynPituus.arvo).isEqualTo(arvo)
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenNoHitsWithGeometry() {
        val hanke = createHankeForTest()

        // setting explicitly dao to return empty lists a.k.a "no hits"
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf()
        )
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf()
        )
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf()
        )
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
            mutableMapOf()
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected3FromKatuluokka() {
        val hanke = createHankeForTest()
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)))
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        Mockito.verify(tormaysPaikkaDao).yleisetKatuluokat(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).pyorailyreitit(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).yleisetKatualueet(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).katuluokat(hanke.geometriat!!)

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                3,
                KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected3FromYleisetKatuluokat() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // NO hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, false))
        )
        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(emptyMap())

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                3,
                KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected4FromYleisetKatuluokatAndAlsoHitInKatualue() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                4,
                KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpectedCentralBusinessArea() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )
        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        )
            .thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                2,
                KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpectedCentralBusinessArea_WhenAlsoYleinenKatuluokka1or2() {
        val hanke = createHankeForTest()

        // hit in yleiset katuluokat 1-2
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        // no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                2,
                KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenYleinenKatuluokka1or2_AndNotCentralBusinessArea() {
        val hanke = createHankeForTest()

        // hit in yleiset katuluokat 1-2
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(emptyMap())

        // NO hit in central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                1,
                KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenKatuluokka1or2_AndNotCentralBusinessArea() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )
        // Liikennemaara maximum=0
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(0))))

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                1,
                KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyPriorityPyoraily() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI)))
        )
        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                5,
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyMainPyoraily() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)))
        )
        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                4,
                PyorailyTormaysLuokittelu.PAAREITTI.toString()
            )
        )
    }

    private fun createHankeForTest(): Hanke {
        val hanke = Hanke(
            id = 1,
            hankeTunnus = "kissa1",
            nimi = "hankkeen nimi",
            kuvaus = "lorem ipsum dolor sit amet...",
            onYKTHanke = false,
            alkuPvm = ZonedDateTime.of(2020, 2, 20, 23, 45, 56, 0, TZ_UTC),
            loppuPvm = ZonedDateTime.of(2021, 2, 20, 23, 45, 56, 0, TZ_UTC),
            vaihe = Vaihe.OHJELMOINTI,
            suunnitteluVaihe = null,
            version = 1,
            createdBy = "Tiina",
            createdAt = getCurrentTimeUTC(),
            modifiedBy = null,
            modifiedAt = null,
            saveType = SaveType.DRAFT
        ).apply {
            this.haittaAlkuPvm = this.alkuPvm
            this.haittaLoppuPvm = this.loppuPvm
            this.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.EI_VAIKUTA
            this.kaistaPituusHaitta = KaistajarjestelynPituus.EI_TARVITA
        }

        // adding geometry
        hanke.geometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hanke.geometriat!!.id = 1
        return hanke
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenLiikenneMaaraClassificationThrowsException() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )

        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(
            emptyMap()
        )

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        val exception = assertThrows(TormaysAnalyysiException::class.java) {
            TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
                hanke,
                LuokitteluRajaArvot()
            )
        }

        assertThat(exception).hasMessage(HankeError.HAI1030.errorMessage)
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenKatuluokka1_AndLiikenneMaara1000() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=1000
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(1000))))

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                1,
                KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                2,
                "500 - 1499"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenKatuluokka4_AndLiikenneMaara3500() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                3,
                "1 500-4999"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenNoHitsForTram_ThenClassIsZero() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))
        Mockito.`when`(tormaysPaikkaDao.raitiotiet(hanke.geometriat!!)).thenReturn(emptyMap())

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 3,
                "1 500-4999"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        assertThat(result[LuokitteluType.RAITIOVAUNULIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.RAITIOVAUNULIIKENNE, 0,
                RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenSharedLaneForTram_ThenClassIsCorrect() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        // hit in katuluokat

        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))

        // trams return shared
        Mockito.`when`(tormaysPaikkaDao.raitiotiet(hanke.geometriat!!))
            .thenReturn(
                mutableMapOf(
                    Pair(
                        hanke.geometriat!!.id!!,
                        setOf(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU)
                    )
                )
            )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 3,
                "1 500-4999"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        assertThat(result[LuokitteluType.RAITIOVAUNULIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.RAITIOVAUNULIIKENNE, 4,
                RaitiovaunuTormaysLuokittelu.JAETTU_KAISTA.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenSeparateLaneForTram_ThenClassIsCorrect() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat

        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))

        // trams return shared
        Mockito.`when`(tormaysPaikkaDao.raitiotiet(hanke.geometriat!!))
            .thenReturn(
                mutableMapOf(
                    Pair(
                        hanke.geometriat!!.id!!,
                        setOf(TormaystarkasteluRaitiotiekaistatyyppi.OMA)
                    )
                )
            )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[LuokitteluType.KATULUOKKA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[LuokitteluType.LIIKENNEMAARA]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 3,
                "1 500-4999"
            )
        )
        assertThat(result[LuokitteluType.PYORAILYN_PAAREITTI]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        assertThat(result[LuokitteluType.RAITIOVAUNULIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.RAITIOVAUNULIIKENNE, 3,
                RaitiovaunuTormaysLuokittelu.OMA_KAISTA.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenNoBusHits_ThenClassIsNoBusTraffic() {

        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // no initialization for buses -> no hits to any geometry
        Mockito.`when`(tormaysPaikkaDao.bussiliikenteenKannaltaKriittinenAlue(hanke.geometriat!!))
            .thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(emptyMap())

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 0 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenCriticalAreBusHits() {
        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // hit in critical area for bus traffic
        Mockito.`when`(tormaysPaikkaDao.bussiliikenteenKannaltaKriittinenAlue(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        // var rajaArvot = LuokitteluRajaArvot()
        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 5 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenBusTrafficAmountMax() {
        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // count is top level for rush hour bus traffic
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(
                Pair(
                    hanke.geometriat!!.id!!,
                    setOf(
                        TormaystarkasteluBussireitti("12", 1, 5, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("13", 1, 5, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("14", 1, 5, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("15", 1, 5, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("16", 1, 5, TormaystarkasteluBussiRunkolinja.EI)
                    )
                )
            )
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        // var rajaArvot = LuokitteluRajaArvot()
        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 5 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenBusLineTrunkYes() {

        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // trunk line hit for bus traffic
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(
                Pair(
                    hanke.geometriat!!.id!!,
                    setOf(
                        TormaystarkasteluBussireitti("12", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("13", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("14", 1, 1, TormaystarkasteluBussiRunkolinja.ON), // this matters
                        TormaystarkasteluBussireitti("15", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("16", 1, 1, TormaystarkasteluBussiRunkolinja.EI)
                    )
                )
            )
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 4 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenBusLineTrafficCountBigButNotMax() {

        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // traffic count between 11-20 for buses
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(
                Pair(
                    hanke.geometriat!!.id!!,
                    setOf(
                        TormaystarkasteluBussireitti("12", 1, 2, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("13", 1, 2, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("14", 1, 5, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("15", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("16", 1, 3, TormaystarkasteluBussiRunkolinja.EI)
                    )
                )
            )
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 4 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenBusLineIsAlmostTrunkLine() {

        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // traffic count 6 for buses
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(
                Pair(
                    hanke.geometriat!!.id!!,
                    setOf(
                        TormaystarkasteluBussireitti(
                            "12",
                            1,
                            1,
                            TormaystarkasteluBussiRunkolinja.LAHES
                        ), // this matters
                        TormaystarkasteluBussireitti("14", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("15", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("16", 1, 1, TormaystarkasteluBussiRunkolinja.EI)
                    )
                )
            )
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 3 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenBusLineTrafficAmountMedium() {

        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // traffic count 5-10 for buses
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(
                Pair(
                    hanke.geometriat!!.id!!,
                    setOf( // rush_hour sum is 7
                        TormaystarkasteluBussireitti("12", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("14", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("17", 1, 3, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("15", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("16", 1, 1, TormaystarkasteluBussiRunkolinja.EI)
                    )
                )
            )
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 3 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenBusLineTrafficAmountSmall() {

        val hanke = createHankeForTest()
        mockTormaysPaikkaDaoForBusClassification(hanke)

        // traffic count 5-10 for buses
        Mockito.`when`(tormaysPaikkaDao.bussit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(
                Pair(
                    hanke.geometriat!!.id!!,
                    setOf( // rush_hour sum is 4
                        TormaystarkasteluBussireitti("12", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("14", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("15", 1, 1, TormaystarkasteluBussiRunkolinja.EI),
                        TormaystarkasteluBussireitti("16", 1, 1, TormaystarkasteluBussiRunkolinja.EI)
                    )
                )
            )
        )

        val result = TormaystarkasteluLuokitteluServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        val expected = LuokitteluRajaArvot().bussiliikenneRajaArvot.first { rajaArvot -> rajaArvot.arvo == 2 }
        assertThat(result[LuokitteluType.BUSSILIIKENNE]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.BUSSILIIKENNE, expected.arvo, expected.explanation
            )
        )
    }

    /**
     * Sets other geometry classification mockings so that in bus test cases we can concentrate in
     * setting the bus traffic cases
     */
    private fun mockTormaysPaikkaDaoForBusClassification(hanke: Hanke) {
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(emptyMap())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(emptyMap())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(emptyMap())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))
    }
}
