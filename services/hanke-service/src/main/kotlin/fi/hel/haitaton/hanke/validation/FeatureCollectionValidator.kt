package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import org.geojson.FeatureCollection
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class FeatureCollectionValidator : ConstraintValidator<ValidFeatureCollection, FeatureCollection> {

    override fun isValid(value: FeatureCollection?, context: ConstraintValidatorContext): Boolean {
        if (value == null) {
            context.buildConstraintViolationWithTemplate("HAI1011:null").addConstraintViolation()
            return false
        }
        var ok = true
        if (value.features.isNullOrEmpty()) {
            context.buildConstraintViolationWithTemplate("HAI1011:Invalid geometry").addPropertyNode("features").addConstraintViolation()
            ok = false
        }
        when {
            value.crs == null -> {
                context.buildConstraintViolationWithTemplate("HAI1011:Invalid geometry").addPropertyNode("crc").addConstraintViolation()
                ok = false
            }
            value.crs.properties.isNullOrEmpty() -> {
                context.buildConstraintViolationWithTemplate("HAI1011:Invalid geometry").addPropertyNode("crc").addPropertyNode("properties").addConstraintViolation()
                ok = false
            }
            value.crs?.properties?.get("name")?.toString() != COORDINATE_SYSTEM_URN -> {
                context.buildConstraintViolationWithTemplate("HAI1013:Invalid coordinate system").addPropertyNode("crc").addPropertyNode("properties").addPropertyNode("value").inIterable().atKey("name").addConstraintViolation()
                ok = false
            }
        }
        return ok
    }
}