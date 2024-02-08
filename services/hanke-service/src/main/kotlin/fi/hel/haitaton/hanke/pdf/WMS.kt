package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.toJsonString
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import javax.imageio.ImageIO
import org.geojson.Crs
import org.geojson.Polygon as JsonPolygon
import org.geotools.api.feature.simple.SimpleFeature
import org.geotools.api.feature.simple.SimpleFeatureType
import org.geotools.brewer.styling.builder.PolygonSymbolizerBuilder
import org.geotools.data.DataUtilities
import org.geotools.data.simple.SimpleFeatureCollection
import org.geotools.feature.simple.SimpleFeatureBuilder
import org.geotools.geometry.jts.ReferencedEnvelope
import org.geotools.map.FeatureLayer
import org.geotools.map.MapContent
import org.geotools.ows.wms.CRSEnvelope
import org.geotools.ows.wms.Layer
import org.geotools.ows.wms.StyleImpl
import org.geotools.ows.wms.WMSUtils
import org.geotools.ows.wms.WebMapServer
import org.geotools.ows.wms.map.WMSLayer
import org.geotools.referencing.CRS
import org.geotools.renderer.GTRenderer
import org.geotools.renderer.lite.StreamingRenderer
import org.locationtech.jts.geom.CoordinateFilter
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.io.geojson.GeoJsonReader

// @Component
object WMS {
    private fun debug(v: Any) {
        println("ASDF $v")
    }

    private fun debug(title: String, v: Any) {
        println("ASDF $title $v")
    }

    // @EventListener(ApplicationReadyEvent::class)
    fun areaImage(polygons: List<JsonPolygon>): ByteArray {
        println("ASDF Starting WMS part:")

        val wms = WebMapServer(URL(INFO_URL))

        val capabilities = wms.capabilities

        val serverName = capabilities.service.name
        val serverTitle = capabilities.service.title
        println("ASDF Capabilities retrieved from server: $serverName ($serverTitle)")

        if (capabilities.request.getFeatureInfo != null) {
            println(
                "ASDF capabilities.request.getFeatureInfo.get ${capabilities.request.getFeatureInfo.get}"
            )
        }

        // capabilities.request.getMap.formats.forEach { println("ASDF Format: $it") }

        val kantakarttaLayer =
            WMSUtils.getNamedLayers(capabilities).find { it.title == KANTAKARTTA_LAYER_TITLE }!!

        // Print layer info
        println("Layer: ${kantakarttaLayer.name}")
        println("       ${kantakarttaLayer.title}")
        println("       ${kantakarttaLayer.children.size}")
        println("       ${kantakarttaLayer.getBoundingBoxes()}")
        val env: CRSEnvelope = kantakarttaLayer.getLatLonBoundingBox()
        println("       " + env.lowerCorner + " x " + env.upperCorner)

        // Get layer styles
        val styles: List<*> = kantakarttaLayer.getStyles()

        val it: Iterator<*> = styles.iterator()
        while (it.hasNext()) {
            val elem: StyleImpl = it.next() as StyleImpl

            // Print style info
            println("Style:")
            println("  Name:" + elem.name)
            println("  Title:" + elem.title)
        }

        val request = wms.createGetMapRequest()
        request.setFormat("image/png")
        request.setDimensions(
            "800",
            "800"
        ) // sets the dimensions of the image to be returned from the server
        request.setTransparent(false)
        request.setSRS("EPSG:3879")
        println("ASDF x size: $y2 - $y1 = ${y2 - y1}")
        println("ASDF y size: $x2 - $x1 = ${x2 - x1}")
        request.setBBox("$y1,$x1,$y2,$x2")

        request.addLayer(kantakarttaLayer)

        val response = wms.issueRequest(request)
        println("ASDF response.contentType ${response.contentType}")

        File("test.png").writeBytes(response.inputStream.readAllBytes())
        println("ASDF Wrote test.png")

        return createMap(wms, kantakarttaLayer, polygons)
    }

    private fun readPolygon(polygon: JsonPolygon): SimpleFeatureCollection {
        val reader = GeoJsonReader()
        polygon.crs = Crs().apply { properties = mapOf(Pair("name", "EPSG:$SRID")) }
        val geometry = reader.read(polygon.toJsonString()) as Polygon
        geometry.coordinates.forEach { println("ASDF coordinate: $it") }
        geometry.apply(
            CoordinateFilter { coordinate ->
                val oldX = coordinate.x
                coordinate.x = coordinate.y
                coordinate.y = oldX
            }
        )
        geometry.coordinates.forEach { println("ASDF reversed coordinate: $it") }
        println("ASDF geometry.geometryType: ${geometry.geometryType}")

        val type: SimpleFeatureType =
            DataUtilities.createType("Polygon", "the_geom:Polygon:srid=$SRID")
        println("TYPE: $type")
        println("type.coordinateReferenceSystem.name ${type.coordinateReferenceSystem.name}")

        val featureBuilder = SimpleFeatureBuilder(type)

        val features: MutableList<SimpleFeature> = mutableListOf()

        featureBuilder.add(geometry)

        features.add(featureBuilder.buildFeature(null))

        val collection = DataUtilities.collection(features)
        println("Collection size: ${collection.size()}")
        println("Collection bounds: ${collection.bounds}")
        println("Collection id: ${collection.id}")
        val feats = collection.features()
        while (feats.hasNext()) {
            val feature = feats.next()
            debug("feature.name", feature.name)
            debug(feature.id)
            debug(feature.attributeCount)
            debug(feature.featureType)
            debug("feature.defaultGeometry", feature.defaultGeometry)
        }

        return collection
    }

    private fun createMap(wms: WebMapServer, layer: Layer, polygons: List<JsonPolygon>): ByteArray {
        val file = "render.png"

        val sourceCRS = CRS.decode("EPSG:3879")

        val mapLayer = WMSLayer(wms, layer, "default-style-avoindata:Kantakartta", "image/png")
        val map = MapContent()

        map.addLayer(mapLayer)

        val blueish = Color(36, 114, 198)
        // val style = SLD.createPolygonStyle(Color.BLACK, Color.BLUE, .0f)

        val builder = PolygonSymbolizerBuilder()
        builder.stroke().color(Color.BLACK).width(4.0)
        builder.fill().color(blueish).opacity(0.8)
        val style = builder.buildStyle()

        // val polygon = OBJECT_MAPPER.readValue(geoJson, JsonPolygon::class.java)
        polygons.forEach {
            val featureLayer = FeatureLayer(readPolygon(it), style)
            map.addLayer(featureLayer)
            println("ASDF featureLayer.isVisible ${featureLayer.isVisible}")
        }

        println("ASDF map.coordinateReferenceSystem ${map.coordinateReferenceSystem.name}")

        val renderer: GTRenderer = StreamingRenderer()
        renderer.mapContent = map

        val imageBounds: Rectangle?
        val mapBounds = ReferencedEnvelope(x1 * 1.0, x2 * 1.0, y1 * 1.0, y2 * 1.0, sourceCRS)
        try {
            val heightToWidth = mapBounds.getSpan(1) / mapBounds.getSpan(0)
            imageBounds =
                Rectangle(0, 0, IMAGE_WIDTH, Math.round(IMAGE_WIDTH * heightToWidth).toInt())
        } catch (e: Exception) {
            // failed to access map layers
            throw RuntimeException(e)
        }

        with(mapBounds) { println("ASDF $minX $minY $maxX $maxY") }

        val image = BufferedImage(imageBounds.width, imageBounds.height, BufferedImage.TYPE_INT_RGB)

        val gr = image.createGraphics()
        gr.paint = Color.WHITE
        gr.fill(imageBounds)

        try {
            renderer.paint(gr, imageBounds, mapBounds)
            val fileToSave = File(file)
            ImageIO.write(image, "png", fileToSave)
            println("ADSF Wrote $file")
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            return baos.toByteArray()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    // companion object {
    const val KANTAKARTTA_LAYER_TITLE = "Kantakartta"
    const val INFO_URL =
        "https://kartta.hel.fi/ws/geoserver/avoindata/wms?REQUEST=GetCapabilities&SERVICE=WMS&VERSION=1.1.1"

    const val IMAGE_WIDTH = 1600
    const val IMAGE_HEIGHT = 1600

    val padding = 30
    val y1 = 25493996 - padding
    val y2 = 25493996 + padding
    val x1 = 6679878 - padding
    val x2 = 6679878 + padding

    val geoJson =
        """{
                "crs": {"type": "name", "properties": {"name": "urn:ogc:def:crs:EPSG::3879"}},
                "type": "Polygon", "coordinates": [[
                [25493996.96814304, 6679878.954325488],
                [25493998.09339202, 6679877.360834192],
                [25493999.686883315,6679878.486083172],
                [25493998.561634336,6679880.079574469],
                [25493996.96814304, 6679878.954325488]
               ]]
            }"""
    // }
}
