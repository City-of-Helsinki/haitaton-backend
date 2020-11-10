package fi.hel.haitaton.hanke

import com.fasterxml.jackson.module.kotlin.readValue
import org.geojson.FeatureCollection

class HankeDaoImpl : HankeDao {
    override fun findHankeByHankeId(hankeId: String): HankeEntity? {
        // TODO proper implementation
        return HankeEntity()
    }

    override fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometria: FeatureCollection) {
        // TODO
    }

    override fun loadHankeGeometria(hanke: HankeEntity): FeatureCollection? {
        // TODO
        val content = """
            {
              "type": "FeatureCollection",
              "crs": {
                "type": "name",
                "properties": {
                  "name": "urn:ogc:def:crs:EPSG::3879"
                }
              },
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [
                      24.948462,
                      60.174095
                    ]
                  }
                },
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "Point",
                    "coordinates": [
                      24.9447,
                      60.172268
                    ]
                  },
                  "properties": {
                    "geometryType": "KUOPPA"
                  }
                }
              ]
            }
        """.trimIndent()
        return OBJECT_MAPPER.readValue<FeatureCollection>(content)
    }

    override fun saveHanke(hanke: Hanke): HankeEntity {
        TODO("Not yet implemented")
        return HankeEntity()
    }
}
