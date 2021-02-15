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

    private val GEOMETRY_ID = 1

    @Configuration
    class TestConfiguration {
        @Bean
        fun tormaysPaikkaDao(): TormaystarkasteluDao = Mockito.mock(TormaystarkasteluDao::class.java)
    }

    private var tormaysPaikkaDao: TormaystarkasteluDao = Mockito.mock(TormaystarkasteluDao::class.java)

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenNoHitsWithGeometry() {

        //setting explicitly dao to return empty lists a.k.a "no hits"
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf()
        )
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf()
        )
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf()
        )
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(
                mutableMapOf()
        )

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))

    }


    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected3FromKatuluokka() {

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )
        //hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU))
        )

        //central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected3FromYleisetKatuluokat() {

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.PAIKALLINEN_KOKOOJAKATU))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //NO hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, false))
        )

        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 3, KatuluokkaTormaysLuokittelu.PAIKALLINEN_KOKOOJA.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpected4FromYleisetKatuluokatAndAlsoHitInKatualue() {

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU))
        )

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )
        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())

        // should no not have affect: central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 4, KatuluokkaTormaysLuokittelu.ALUEELLINEN_KOKOOJA.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }


    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpectedCentralBusinessArea() {

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )
        //hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS))
        )

        //central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }


    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenExpectedCentralBusinessArea_WhenAlsoYleinenKatuluokka1or2() {

        //hit in yleiset katuluokat 1-2
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 2, KatuluokkaTormaysLuokittelu.KANTAKAUPUNGIN_TONTTIKATU.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenYleinenKatuluokka1or2_AndNotCentralBusinessArea() {

        //hit in yleiset katuluokat 1-2
        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS)))

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )

        //no hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())

        // NO hit in central_business_area hit -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(mutableMapOf())

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_WhenKatuluokka1or2_AndNotCentralBusinessArea() {

        Mockito.`when`(tormaysPaikkaDao.yleisetKatuluokat(GEOMETRY_ID)).thenReturn(mutableMapOf())
        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(mutableMapOf())

        //hit in yleiset katualueet
        Mockito.`when`(tormaysPaikkaDao.yleisetKatualueet(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, true))
        )
        //hit in katuluokat
        Mockito.`when`(tormaysPaikkaDao.katuluokat(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluKatuluokka.TONTTIKATU_TAI_AJOYHTEYS))
        )

        // NO hit in central_business_area -> kantakaupunki
        Mockito.`when`(tormaysPaikkaDao.kantakaupunki(GEOMETRY_ID)).thenReturn(mutableMapOf())

        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(),
                LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 1, KatuluokkaTormaysLuokittelu.MUU_TONTTIKATU_ALUE.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 0, PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()))
    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyPriorityPyoraily() {

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluPyorailyreittiluokka.PRIORISOITU_REITTI))
        )
        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 5, PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyMainPyoraily() {

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(
                mutableMapOf(Pair(GEOMETRY_ID, TormaystarkasteluPyorailyreittiluokka.PAAREITTI))
        )
        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.EI_MOOTTORILIIKENNE_VAIK.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 4, PyorailyTormaysLuokittelu.PAAREITTI.toString()))

    }


    fun createHankeForTest(): Hanke {
        var hanke = Hanke(id = 1, hankeTunnus = "kissa1",
                nimi = "hankkeen nimi", kuvaus = "lorem ipsum dolor sit amet...", onYKTHanke = false,
                alkuPvm = ZonedDateTime.of(2020, 2, 20, 23, 45, 56, 0, TZ_UTC), loppuPvm = ZonedDateTime.of(2021, 2, 20, 23, 45, 56, 0, TZ_UTC), vaihe = Vaihe.OHJELMOINTI, suunnitteluVaihe = null,
                version = 1, createdBy = "Tiina", createdAt = getCurrentTimeUTC(), modifiedBy = null, modifiedAt = null, saveType = SaveType.DRAFT)

        //adding geometry
        hanke.geometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hanke.geometriat!!.id = GEOMETRY_ID
        return hanke
    }

}