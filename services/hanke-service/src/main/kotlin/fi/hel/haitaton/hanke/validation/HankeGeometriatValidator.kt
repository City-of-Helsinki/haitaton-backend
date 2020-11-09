package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.COORDINATE_SYSTEM_URN
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeGeometriat
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class HankeGeometriatValidator : ConstraintValidator<ValidHankeGeometriat, HankeGeometriat> {

    override fun isValid(value: HankeGeometriat?, context: ConstraintValidatorContext): Boolean {
        if (value?.featureCollection == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addConstraintViolation()
            return false
        }
        var ok = true
        if (value.featureCollection.features.isNullOrEmpty()) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addPropertyNode("featureCollection").addPropertyNode("features").addConstraintViolation()
            ok = false
        }
        when {
            value.featureCollection.crs == null -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addPropertyNode("featureCollection").addPropertyNode("crs").addConstraintViolation()
                ok = false
            }
            value.featureCollection.crs.properties.isNullOrEmpty() -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1011.name).addPropertyNode("featureCollection").addPropertyNode("crs").addPropertyNode("properties").addConstraintViolation()
                ok = false
            }
            value.featureCollection.crs?.properties?.get("name")?.toString() != COORDINATE_SYSTEM_URN -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1013.name).addPropertyNode("featureCollection").addPropertyNode("crs").addPropertyNode("properties").addPropertyNode(null).inIterable().atKey("name").addConstraintViolation()
                ok = false
            }
        }
        return ok
    }
}