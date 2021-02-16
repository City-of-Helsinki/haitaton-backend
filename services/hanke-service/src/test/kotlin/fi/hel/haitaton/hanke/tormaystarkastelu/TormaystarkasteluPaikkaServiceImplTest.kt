package fi.hel.haitaton.hanke.tormaystarkastelu


import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import org.assertj.core.api.Assertions.assertThat

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

        //setting explicitly dao to return empty lists a.k.a "no hits"
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
            hanke, LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))

    }


    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected3FromKatuluokka() {
        val hanke = createHankeForTest()
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        //hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)))
        )

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot())

        Mockito.verify(tormaysPaikkaDao).yleisetKatuluokat(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).pyorailyreitit(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).yleisetKatualueet(hanke.geometriat!!)
        Mockito.verify(tormaysPaikkaDao).katuluokat(hanke.geometriat!!)

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected3FromYleisetKatuluokat() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU)))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //NO hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, false))
        )

        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(
            hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected4FromYleisetKatuluokatAndAlsoHitInKatualue() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU)))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 4, KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }


    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpectedCentralBusinessArea() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        //hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )

        //central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }


    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpectedCentralBusinessArea_WhenAlsoYleinenKatuluokka1or2() {
        val hanke = createHankeForTest()

        //hit in yleiset katuluokat 1-2
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS))))

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenYleinenKatuluokka1or2_AndNotCentralBusinessArea() {
        val hanke = createHankeForTest()

        //hit in yleiset katuluokat 1-2
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS))))

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )

        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())

        // NO hit in central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenKatuluokka1or2_AndNotCentralBusinessArea() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(hanke.geometriat!!)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, true))
        )
        //hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))
        )

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(hanke.geometriat!!)).thenReturn(mutableMapOf())

        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke,
                LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyPriorityPyoraily() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI)))
        )
        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 5, PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyMainPyoraily() {
        val hanke = createHankeForTest()

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(hanke.geometriat!!)).thenReturn(
                mutableMapOf(Pair(hanke.geometriat!!.id!!, setOf(TormaystarkasteluPyorailyreittiluokka.PAAREITTI)))
        )
        val result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(hanke, LuokitteluRajaArvot())

        assertThat(result[0]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()))
        assertThat(result[1]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result[2]).isEqualTo(Luokittelutulos(hanke.geometriat!!.id!!, LuokitteluType.PYORAILYN_PAAREITTI, 4, PyorailyTormaysLuokittelu.PAAREITTI.toString()))

    }

    private fun createHankeForTest(): Hanke {
        val hanke = Hanke(id = 1, hankeTunnus = "kissa1",
                nimi = "hankkeen nimi", kuvaus = "lorem ipsum dolor sit amet...", onYKTHanke = false,
                alkuPvm = ZonedDateTime.of(2020, 2, 20, 23, 45, 56, 0, TZ_UTC), loppuPvm = ZonedDateTime.of(2021, 2, 20, 23, 45, 56, 0, TZ_UTC), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
                version = 1, createdBy = "Tiina", createdAt = getCurrentTimeUTC(), modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        //adding geometry
        hanke.geometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hanke.geometriat!!.id = 1
        return hanke
    }

}