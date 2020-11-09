package fi.hel.haitaton.hanke

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.ZonedDateTime

class HankeDaoImpl : HankeDao {
    override fun findHankeByHankeId(hankeId: String): HankeEntity? {
        // TODO proper implementation
        return HankeEntity("1234567")
    }

    override fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometriat: HankeGeometriat) {
        // TODO
    }

    override fun loadHankeGeometria(hanke: HankeEntity): HankeGeometriat? {
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
        return HankeGeometriat(hanke.id, OBJECT_MAPPER.readValue(content), 0, ZonedDateTime.now(TZ_UTC), ZonedDateTime.now(TZ_UTC))
    }
}
