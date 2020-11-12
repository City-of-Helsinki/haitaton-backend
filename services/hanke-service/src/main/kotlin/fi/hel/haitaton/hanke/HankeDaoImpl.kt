package fi.hel.haitaton.hanke

import com.fasterxml.jackson.module.kotlin.readValue

class HankeDaoImpl : HankeDao {

    override fun findHankeByHankeId(hankeId: String): OldHankeEntity? {
        // TODO proper implementation
        return OldHankeEntity("1234567")
    }

    override fun saveHankeGeometria(hankeEntity: OldHankeEntity, hankeGeometriat: HankeGeometriat) {
        // TODO
    }

    override fun loadHankeGeometria(hanke: OldHankeEntity): HankeGeometriat? {
        // TODO
        val content = """
            {
              "hankeId": "1234567",
              "featureCollection": {
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
                        24747856.43,
                        6562789.70
                      ]
                    }
                  },
                  {
                    "type": "Feature",
                    "geometry": {
                      "type": "Point",
                      "coordinates": [
                        24747856.43,
                        6562789.70
                      ]
                    },
                    "properties": {
                      "geometryType": "KUOPPA"
                    }
                  }
                ]
              },
              "version": 1,
              "createdAt": "2020-11-09T15:53:23.1234567+02:00[Europe/Helsinki]",
              "updatedAt": "2020-11-09T16:53:23.1234567+02:00[Europe/Helsinki]"
            }
        """.trimIndent()
        return OBJECT_MAPPER.readValue(content)
    }

    override fun saveHanke(hanke: Hanke): OldHankeEntity {
        // TODO("Not yet implemented")
        return OldHankeEntity("id")
    }
}
