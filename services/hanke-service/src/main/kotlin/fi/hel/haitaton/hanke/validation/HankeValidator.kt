package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.domain.Hanke
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class HankeValidator : ConstraintValidator<ValidHanke, Hanke> {


    override fun isValid(hanke: Hanke?, context: ConstraintValidatorContext): Boolean {

        if (hanke == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addConstraintViolation()
            return false
        }

        var ok = true
        if (hanke.createdBy.isNullOrBlank()) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("createdBy").addConstraintViolation()
            ok = false
        }
        when {
            hanke.nimi.isNullOrBlank() -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("nimi").addConstraintViolation()
                ok = false
            }
            hanke.alkuPvm == null -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("alkuPvm").addConstraintViolation()
                ok = false
            }
            hanke.loppuPvm == null -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("loppuPvm").addConstraintViolation()
                ok = false
            }

            // TODO: real phase validation checks when we know what we are passing through
            // TODO: no longer a number, but either a string or enum
//            hanke.vaihe!! > 7 -> {
//                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("vaihe").addConstraintViolation()
//                ok = false
//            }
        }
        return ok
    }
}