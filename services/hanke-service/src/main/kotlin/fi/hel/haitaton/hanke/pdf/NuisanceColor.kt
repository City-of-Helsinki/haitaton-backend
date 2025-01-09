package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.awt.Color
import org.geotools.api.style.Style

enum class NuisanceColor(val color: Color) {
    BLUE(Color(0, 98, 185)),
    GRAY(Color(176, 184, 191)),
    GREEN(Color(0, 146, 70)),
    YELLOW(Color(255, 218, 7)),
    RED(Color(196, 18, 62));

    val style: Style by lazy { MapGenerator.buildAreaStyle(color) }

    companion object {
        fun selectColor(tormaystarkasteluTulos: TormaystarkasteluTulos?): NuisanceColor {
            val maxHaitta = tormaystarkasteluTulos?.liikennehaittaindeksi?.indeksi

            return when {
                maxHaitta == null -> BLUE
                maxHaitta.isNaN() -> BLUE
                maxHaitta < 0f -> BLUE
                maxHaitta == 0f -> GRAY
                maxHaitta < 3f -> GREEN
                maxHaitta < 4f -> YELLOW
                else -> RED
            }
        }
    }
}
