package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN

class GeometriatValidator {
    companion object {
        fun expectValid(value: Geometriat?) {
            val featureCollection =
                value?.featureCollection ?: throw GeometriaValidationException("featureCollection")
            when {
                featureCollection.crs == null ->
                    throw GeometriaValidationException("featureCollection.crs")
                featureCollection.crs.properties.isNullOrEmpty() ->
                    throw GeometriaValidationException("featureCollection.crs.properties")
                featureCollection.crs.properties["name"]?.toString() != COORDINATE_SYSTEM_URN ->
                    throw UnsupportedCoordinateSystemException(
                        featureCollection.crs.properties["name"]?.toString()
                    )
            }
        }
    }
}

class GeometriaValidationException(path: String) :
    RuntimeException("Geometria failed validation at $path")

class UnsupportedCoordinateSystemException(system: String?) :
    RuntimeException("Invalid coordinate system: $system")
