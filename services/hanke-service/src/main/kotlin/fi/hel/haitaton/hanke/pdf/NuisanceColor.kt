package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Font
import java.awt.Color
import org.geotools.api.style.Style

enum class NuisanceColor(val color: Color, val font: Font) {
    BLUE(Color(0, 98, 185), blackNuisanceFont),
    GRAY(Color(176, 184, 191), blackNuisanceFont),
    GREEN(Color(0, 146, 70), whiteNuisanceFont),
    YELLOW(Color(255, 218, 7), blackNuisanceFont),
    RED(Color(196, 18, 62), whiteNuisanceFont),
    LAVENDER(Color(0xe6, 0xef, 0xf8), blackNuisanceFont);

    val style: Style by lazy { MapGenerator.buildAreaStyle(color) }

    companion object {
        fun selectColor(index: Float?): NuisanceColor {
            return when {
                index == null -> BLUE
                index.isNaN() -> BLUE
                index < 0f -> BLUE
                index == 0f -> GRAY
                index < 3f -> GREEN
                index < 4f -> YELLOW
                else -> RED
            }
        }
    }
}
