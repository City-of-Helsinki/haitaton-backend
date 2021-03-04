package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeTilat
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

internal class TormaystarkasteluLaskentaServiceImplTest {

    private val hankeService: HankeService = mockk()

    private val luokitteluService: TormaystarkasteluLuokitteluService = mockk()

    private val geometriatService: HankeGeometriatService = mockk()

    private val tormaystarkasteluLaskentaService =
        TormaystarkasteluLaskentaServiceImpl(hankeService, luokitteluService, geometriatService)

    @Test
    fun calculateTormaystarkastelu() {
        val hanke = Hanke(1, "HAI21-1").apply {
            this.tilat = HankeTilat(onTiedotLiikenneHaittaIndeksille = true)
        }
        every { hankeService.loadHanke("HAI21-1") } returns hanke
        val hankeGeometriat = HankeGeometriat()
        every { geometriatService.loadGeometriat(hanke) } returns hankeGeometriat
        val luokittelutulos = LuokitteluType.values().associateWith { Luokittelutulos(it, 1, "") }
        every { luokitteluService.calculateTormaystarkasteluLuokitteluTulos(eq(hanke), any()) } returns luokittelutulos
        every { hankeService.updateHanke(hanke) } returns hanke

        tormaystarkasteluLaskentaService.calculateTormaystarkastelu("HAI21-1")

        println(hanke.tormaystarkasteluTulos)
        assertThat(hanke.liikennehaittaindeksi).isEqualTo(
            LiikennehaittaIndeksiType(
                1.0f,
                IndeksiType.JOUKKOLIIKENNEINDEKSI
            )
        )
        assertThat(hanke.tormaystarkasteluTulos).isNotNull()
        assertThat(hanke.tormaystarkasteluTulos!!.liikennehaittaIndeksi).isEqualTo(
            LiikennehaittaIndeksiType(
                1.0f,
                IndeksiType.JOUKKOLIIKENNEINDEKSI
            )
        )
    }

    @Test
    fun getTormaystarkastelu() {
        // TODO
    }
}
