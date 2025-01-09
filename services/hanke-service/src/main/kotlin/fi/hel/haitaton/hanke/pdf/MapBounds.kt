package fi.hel.haitaton.hanke.pdf

import java.util.Locale
import kotlin.math.max
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.referencing.CRS

data class Point(val x: Double, val y: Double) {

    private fun fixedPoint(a: Double): String = String.format(Locale.UK, "%.3f", a)

    override fun toString(): String = "(x=${fixedPoint(x)}, y=${fixedPoint(y)})"

    companion object {
        fun center(a: Point, b: Point) = Point(y = (a.y + b.y) / 2.0, x = (a.x + b.x) / 2.0)
    }
}

data class MapBounds(val min: Point, val max: Point) {
    val xSize: Double = max.x - min.x
    val ySize: Double = max.y - min.y
    val center: Point = Point.center(min, max)

    fun padded(): MapBounds {
        val xPadding = max(xSize * PADDING_RATIO, MIN_PADDING)
        val yPadding = max(ySize * PADDING_RATIO, MIN_PADDING)

        return MapBounds(
            Point(y = min.y - yPadding, x = min.x - xPadding),
            Point(y = max.y + yPadding, x = max.x + xPadding),
        )
    }

    fun fitToImage(imageWidth: Int, imageHeight: Int): MapBounds {
        val metersPerPixel = max(xSize / imageWidth, ySize / imageHeight)

        val newXSize = imageWidth * metersPerPixel
        val newYSize = imageHeight * metersPerPixel

        return MapBounds(
            Point(y = center.y - newYSize / 2, x = center.x - newXSize / 2),
            Point(y = center.y + newYSize / 2, x = center.x + newXSize / 2),
        )
    }

    fun squaredOff(): MapBounds {
        val maxSize = max(xSize, ySize)

        return MapBounds(
            Point(y = center.y - maxSize / 2, x = center.x - maxSize / 2),
            Point(y = center.y + maxSize / 2, x = center.x + maxSize / 2),
        )
    }

    fun asReferencedEnvelope() = ReferencedEnvelope(min.y, max.y, min.x, max.x, sourceCRS)

    companion object {
        const val MIN_PADDING = 30.0
        const val PADDING_RATIO = 0.1

        val sourceCRS: CoordinateReferenceSystem = CRS.decode("EPSG:3879")
    }
}
