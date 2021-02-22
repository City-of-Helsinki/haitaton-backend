package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TormaysAnalyysiException
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.ZonedDateTime

@ExtendWith(SpringExtension::class)
@Import(TormaystarkasteluPaikkaServiceImplTest.TestConfiguration::class)
internal class TormaystarkasteluPaikkaServiceImplTest {

    @Configuration
    class TestConfiguration {
        @Bean
        fun tormaysPaikkaDao(): TormaystarkasteluDao = Mockito.mock(TormaystarkasteluDao::class.java)
    }

    private var tormaysPaikkaDao: TormaystarkasteluDao = Mockito.mock(TormaystarkasteluDao::class.java)

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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result.get(0)).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        )
        assertThat(result.get(1)).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result.get(2)).isEqualTo(
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
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        Mockito.verify(tormaysPaikkaDao).yleisetKatuluokat(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).pyorailyreitit(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).yleisetKatualueet(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).katuluokat(hanke.geometriat!!)

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                3,
                KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

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
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                3,
                KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                4,
                KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                2,
                KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        // no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                2,
                KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

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
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // NO hit in central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                1,
                KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

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
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                1,
                KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI)))
        )
        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)))
        )
        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                0,
                KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                0,
                "Ei autoliikennettä"
            )
        )
        assertThat(result[2]).isEqualTo(
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
        )

        // adding geometry
        hanke.geometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hanke.geometriat!!.id = 1
        return hanke
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenLiikenneMaaraClassificationThrowsException() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        val exception = assertThrows(TormaysAnalyysiException::class.java) {
            TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
                hanke,
                LuokitteluRajaArvot()
            )
        }

        assertThat(exception).hasMessage(HankeError.HAI1030.errorMessage)
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenKatuluokka1_AndLiikenneMaara1000() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // Liikennemaara maximum=1000
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(1000))))

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                1,
                KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                2,
                "500 - 1499"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.LIIKENNEMAARA,
                3,
                "1 500-4999"
            )
        )
        assertThat(result[2]).isEqualTo(
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

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // Liikennemaara maximum=3500, should use bigger buffered volumes
        Mockito.`when`(
            tormaysPaikkaDao.liikennemaarat(
                hanke.geometriat!!,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        ).thenReturn(mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(3500))))

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke,
            LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!,
                LuokitteluType.KATULUOKKA,
                5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 3,
                "1 500-4999"
            )
        )
        assertThat(result[2]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        assertThat(result[3]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.RAITIOVAUNULIIKENNE, 0,
                RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenSharedLaneForTram_ThenClassIsCorrect() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 3,
                "1 500-4999"
            )
        )
        assertThat(result[2]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        assertThat(result[3]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.RAITIOVAUNULIIKENNE, 4,
                RaitiovaunuTormaysLuokittelu.JAETTU_KAISTA.toString()
            )
        )
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenSeparateLaneForTram_ThenClassIsCorrect() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
            mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        // hit in katuluokat
        val katuluokat =
            mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAAKATU_TAI_MOOTTORIVAYLA)))
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(katuluokat)

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

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

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot()
        )

        assertThat(result[0]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 5,
                KatuluokkaTormaysLuokittelu.PAAKATU_MOOTTORIVAYLA.toString()
            )
        )
        assertThat(result[1]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 3,
                "1 500-4999"
            )
        )
        assertThat(result[2]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        assertThat(result[3]).isEqualTo(
            Luokittelutulos(
                hanke.geometriat!!.id!!, LuokitteluType.RAITIOVAUNULIIKENNE, 3,
                RaitiovaunuTormaysLuokittelu.OMA_KAISTA.toString()
            )
        )
    }
}
