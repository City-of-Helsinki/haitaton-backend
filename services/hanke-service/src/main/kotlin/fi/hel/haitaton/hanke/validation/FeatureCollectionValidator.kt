package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.HankeError
import org.geojson.FeatureCollection
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class FeatureCollectionValidator : ConstraintValidator<ValidFeatureCollection, FeatureCollection> {

    override fun isValid(value: FeatureCollection?, context: ConstraintValidatorContext): Boolean {
        if (value == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addConstraintViolation()
            return false
        }
        var ok = true
        if (value.features.isNullOrEmpty()) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addPropertyNode("features").addConstraintViolation()
            ok = false
        }
        when {
            value.crs == null -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addPropertyNode("crs").addConstraintViolation()
                ok = false
            }
            value.crs.properties.isNullOrEmpty() -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addPropertyNode("crs").addPropertyNode("properties").addConstraintViolation()
                ok = false
            }
            value.crs?.properties?.get("name")?.toString() != COORDINATE_SYSTEM_URN -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1013.name).addPropertyNode("crs").addPropertyNode("properties").addPropertyNode("value").inIterable().atKey("name").addConstraintViolation()
                ok = false
            }
        }
        return ok
    }
}