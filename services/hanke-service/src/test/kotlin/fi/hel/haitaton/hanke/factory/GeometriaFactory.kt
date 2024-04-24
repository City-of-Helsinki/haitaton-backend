package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.NewGeometriat
import fi.hel.haitaton.hanke.geometria.Geometriat
import org.geojson.Polygon

object GeometriaFactory {

    val polygon: Polygon = "/fi/hel/haitaton/hanke/geometria/polygon.json".asJsonResource()
    val secondPolygon: Polygon =
        "/fi/hel/haitaton/hanke/geometria/toinen_polygoni.json".asJsonResource()
    val thirdPolygon: Polygon =
        "/fi/hel/haitaton/hanke/geometria/kolmas_polygoni.json".asJsonResource()

    /**
     * Same geometry as [secondPolygon], that's used as default in Application factory for
     * application areas.
     */
    fun create(id: Int = 1): Geometriat =
        "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource<Geometriat>()
            .copy(id = id)

    fun createNew(): NewGeometriat = NewGeometriat(create().featureCollection)
}
