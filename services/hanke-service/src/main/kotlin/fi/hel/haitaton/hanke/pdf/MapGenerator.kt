package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import org.geojson.Crs
import org.geojson.Polygon as JsonPolygon
import org.geotools.api.feature.simple.SimpleFeature
import org.geotools.api.feature.simple.SimpleFeatureType
import org.geotools.api.style.Style
import org.geotools.brewer.styling.builder.PolygonSymbolizerBuilder
import org.geotools.data.DataUtilities
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.map.FeatureLayer
import org.geotools.map.MapContent
import org.geotools.ows.wms.WMSUtils
import org.geotools.ows.wms.WebMapServer
import org.geotools.ows.wms.map.WMSLayer
import org.geotools.renderer.GTRenderer
import org.geotools.renderer.lite.StreamingRenderer
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.geojson.GeoJsonReader
import org.springframework.stereotype.Component

@Component
class MapGenerator(private val wms: WebMapServer) {

    fun mapWithAreas(
        areas: List<KaivuilmoitusAlue>,
        hankealueet: List<SavedHankealue>,
        imageWidth: Int,
        imageHeight: Int,
        getIndex: (TormaystarkasteluTulos?) -> Float?,
    ): ByteArray {
        val bounds = calculateBounds(areas, imageWidth, imageHeight)

        val layer =
            WMSUtils.getNamedLayers(wms.capabilities).find {
                it.title == KIINTEISTOKARTTA_LAYER_TITLE
            }!!
        val mapLayer = WMSLayer(wms, layer, KIINTEISTOKARTTA_STYLE, "image/png")

        val map = MapContent()
        map.addLayer(mapLayer)

        hankealueet.forEach { alue ->
            val polygons =
                alue.geometriat
                    ?.featureCollection
                    ?.features
                    ?.map { it.geometry }
                    ?.filterIsInstance<JsonPolygon>() ?: listOf()

            val style = NuisanceColor.selectColor(getIndex(alue.tormaystarkasteluTulos)).style
            val featureLayer = FeatureLayer(readPolygons(polygons), style)
            map.addLayer(featureLayer)
        }

        areas
            .flatMap { it.tyoalueet }
            .forEach {
                val style = NuisanceColor.selectColor(getIndex(it.tormaystarkasteluTulos)).style
                val featureLayer = FeatureLayer(readPolygon(it.geometry), style)
                map.addLayer(featureLayer)
            }

        val renderer: GTRenderer = StreamingRenderer()
        renderer.mapContent = map

        val envelope = bounds.asReferencedEnvelope()

        // The maps are distorted, unless we request a square map. So we request a square and then
        // crop it to fit the requested image dimensions.
        val imageSize = max(imageWidth, imageHeight)
        val imageBounds = Rectangle(0, 0, imageSize, imageSize)
        val image = BufferedImage(imageBounds.width, imageBounds.height, BufferedImage.TYPE_INT_RGB)

        val gr = image.createGraphics()
        gr.paint = Color.WHITE
        gr.fill(imageBounds)

        val cropped =
            image.getSubimage(
                (imageSize - imageWidth) / 2,
                (imageSize - imageHeight) / 2,
                imageWidth,
                imageHeight,
            )

        renderer.paint(gr, imageBounds, envelope)
        val baos = ByteArrayOutputStream()
        ImageIO.write(cropped, "png", baos)
        map.dispose()
        return baos.toByteArray()
    }

    companion object {
        const val KIINTEISTOKARTTA_LAYER_TITLE = "Kiinteistokartan_maastotiedot"
        const val KIINTEISTOKARTTA_STYLE = "default-style-avoindata:Kiinteistokartan_maastotiedot"

        private val polygonType: SimpleFeatureType =
            DataUtilities.createType("Polygon", "the_geom:Polygon:srid=$SRID")

        fun calculateBounds(
            areas: List<KaivuilmoitusAlue>,
            imageWidth: Int,
            imageHeight: Int,
        ): MapBounds {
            val allCoords = areas.flatMap { it.geometries() }.flatMap { it.coordinates.flatten() }

            val latitudes = allCoords.map { it.latitude }
            val longitudes = allCoords.map { it.longitude }
            val bounds =
                MapBounds(
                    Point(y = latitudes.min(), x = longitudes.min()),
                    Point(y = latitudes.max(), x = longitudes.max()),
                )

            return bounds.padded().fitToImage(imageWidth, imageHeight).squaredOff()
        }

        private fun readPolygon(polygon: JsonPolygon): SimpleFeatureCollection =
            readPolygons(listOf(polygon))

        private fun readPolygons(polygons: List<JsonPolygon>): SimpleFeatureCollection {
            val reader = GeoJsonReader()

            val features: MutableList<SimpleFeature> = mutableListOf()

            polygons.forEach { polygon ->
                val copy = JsonPolygon()
                copy.exteriorRing = polygon.exteriorRing
                copy.interiorRings.addAll(polygon.interiorRings)
                copy.crs = Crs().apply { properties = mapOf(Pair("name", "EPSG:$SRID")) }
                val geometry = reader.read(copy.toJsonString()) as Polygon

                // I'm not quite sure why, but the x and y coordinates seem to be reversed in
                // different
                // implementations. Currently, the maps are drawn correctly, so I don't want to mess
                // with them. The coordinates are reversed here and when calculating the final map
                // boundaries for the image (the referenced envelope).
                geometry.apply(
                    CoordinateFilter { coordinate ->
                        val oldX = coordinate.x
                        coordinate.x = coordinate.y
                        coordinate.y = oldX
                    }
                )
                val featureBuilder = SimpleFeatureBuilder(polygonType)
                featureBuilder.add(geometry)
                features.add(featureBuilder.buildFeature(null))
            }

            return DataUtilities.collection(features)
        }

        fun buildAreaStyle(color: Color): Style {
            val builder = PolygonSymbolizerBuilder()
            builder.stroke().color(Color.BLACK).width(4.0)
            builder.fill().color(color).opacity(0.6)
            return builder.buildStyle()
        }
    }
}
