package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.SRID
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.NewGeometriat
import fi.hel.haitaton.hanke.geometria.Geometriat
import org.geojson.Crs
import org.geojson.GeometryCollection
import org.geojson.Polygon

object GeometriaFactory {

    fun polygon(): Polygon = "/fi/hel/haitaton/hanke/geometria/polygon.json".asJsonResource()

    fun secondPolygon(): Polygon =
        "/fi/hel/haitaton/hanke/geometria/toinen_polygoni.json".asJsonResource()

    fun thirdPolygon(): Polygon =
        "/fi/hel/haitaton/hanke/geometria/kolmas_polygoni.json".asJsonResource()

    fun fourthPolygon(): Polygon =
        "/fi/hel/haitaton/hanke/geometria/neljas_polygoni.json".asJsonResource()

    fun collection(vararg polygon: Polygon = arrayOf(secondPolygon())): GeometryCollection =
        GeometryCollection().apply {
            polygon.forEach { add(it) }
            crs = Crs()
            crs.properties["name"] = "EPSG:$SRID"
        }

    /**
     * Same geometry as [secondPolygon], that's used as default in Application factory for
     * application areas.
     */
    fun create(id: Int = 1): Geometriat =
        "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource<Geometriat>()
            .copy(id = id)

    fun create(id: Int = 1, geometria: Polygon): Geometriat {
        val geometriat = create(id)
        geometriat.featureCollection?.apply {
            val feature = features.first()
            feature.geometry = geometria
        }
        return geometriat
    }

    fun createNew(): NewGeometriat = NewGeometriat(create().featureCollection)
}
