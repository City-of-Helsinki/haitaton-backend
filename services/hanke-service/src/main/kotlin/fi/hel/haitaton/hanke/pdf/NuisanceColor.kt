package fi.hel.haitaton.hanke.pdf

import com.lowagie.text.Font
import java.awt.Color
import org.geotools.api.style.Style
import org.geotools.brewer.styling.builder.PolygonSymbolizerBuilder

enum class NuisanceColor(val color: Color, val font: Font) {
    BLUE(Color(0, 98, 185), blackNuisanceFont),
    GRAY(Color(176, 184, 191), blackNuisanceFont),
    GREEN(Color(0, 146, 70), whiteNuisanceFont),
    YELLOW(Color(255, 218, 7), blackNuisanceFont),
    RED(Color(196, 18, 62), whiteNuisanceFont),
    LAVENDER(Color(0xe6, 0xef, 0xf8), blackNuisanceFont);

    val style: Style by lazy { buildAreaStyle(color) }

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

        private fun buildAreaStyle(color: Color): Style {
            val builder = PolygonSymbolizerBuilder()
            builder.stroke().color(Color.BLACK).width(4.0)
            builder.fill().color(color).opacity(0.6)
            return builder.buildStyle()
        }
    }
}
