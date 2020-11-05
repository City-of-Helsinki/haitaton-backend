package fi.hel.haitaton.hanke

import org.geojson.FeatureCollection

interface HankeGeometriaService {
    fun saveGeometria(hankeId: String, hankeGeometria: FeatureCollection)
}