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

    fun create(): Geometriat =
        "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()

    fun createNew(): NewGeometriat = NewGeometriat(create().featureCollection)
}
