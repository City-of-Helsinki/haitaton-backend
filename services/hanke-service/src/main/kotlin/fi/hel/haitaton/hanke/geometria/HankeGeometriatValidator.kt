package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.GeometriaValidationException

class HankeGeometriatValidator {
    companion object {
        fun exceptValid(value: HankeGeometriat?) {
            val featureCollection =
                value?.featureCollection ?: throw GeometriaValidationException("featureCollection")
            when {
                featureCollection.crs == null -> {
                    throw GeometriaValidationException("featureCollection.crs")
                }
                featureCollection.crs.properties.isNullOrEmpty() -> {
                    throw GeometriaValidationException("featureCollection.crs.properties")
                }
                featureCollection.crs?.properties?.get("name")?.toString() !=
                    COORDINATE_SYSTEM_URN -> {
                    throw GeometriaValidationException("featureCollection.crs.properties.name")
                }
            }
        }
    }
}
