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
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyPriorityPyoraily() {

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(
                mutableListOf(PyorailyTormaystarkastelu(TormaystarkasteluPyorailyreittiluokitus.PRIORISOITU_REITTI, GEOMETRY_ID))
        )
        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 5, PyorailyTormaysLuokittelu.FIVE.toString()))

    }

    @Test
    fun calculateTormaystarkasteluLuokitteluTulos_whenOnlyMainPyoraily() {

        Mockito.`when`(tormaysPaikkaDao.pyorailyreitit(GEOMETRY_ID)).thenReturn(
                mutableListOf(PyorailyTormaystarkastelu(TormaystarkasteluPyorailyreittiluokitus.PAAREITTI, GEOMETRY_ID))
        )
        var result = TormaystarkasteluPaikkaServiceImpl(tormaysPaikkaDao).calculateTormaystarkasteluLuokitteluTulos(createHankeForTest(), LuokitteluRajaArvot())

        assertThat(result.get(0)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.KATULUOKKA, 0, KatuluokkaTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(1)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.LIIKENNEMAARA, 0, LiikenneMaaraTormaysLuokittelu.ZERO.toString()))
        assertThat(result.get(2)).isEqualTo(Luokittelutulos(GEOMETRY_ID, LuokitteluType.PYORAILYN_PAAREITTI, 4, PyorailyTormaysLuokittelu.FOUR.toString()))

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