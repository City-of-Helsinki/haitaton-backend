package fi.hel.haitaton.hanke.validation

import org.geojson.FeatureCollection
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class FeatureCollectionValidator: ConstraintValidator<EPSG3879, FeatureCollection> {
    override fun isValid(value: FeatureCollection?, context: ConstraintValidatorContext?): Boolean {
        return value != null && value.crs?.properties?.get("name")?.toString() == "urn:ogc:def:crs:EPSG::3879"
    }
}