package fi.hel.haitaton.hanke.validation

import fi.hel.haitaton.hanke.Hanke
import fi.hel.haitaton.hanke.HankeError
import java.time.ZonedDateTime
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class HankeValidator : ConstraintValidator<ValidHanke, Hanke> {


    override fun isValid(hanke: Hanke?, context: ConstraintValidatorContext): Boolean {

        if (hanke == null) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addConstraintViolation()
            return false
        }

        var ok = true
        if (hanke.owner.isNullOrBlank()) {
            context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("owner").addConstraintViolation()
            ok = false
        }
        when {
            hanke.name.isNullOrBlank() -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("name").addConstraintViolation()
                ok = false
            }
            hanke.startDate == null -> {  //TODO: these to be added when we are ready to add the mandatory datas to tests
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("startDate").addConstraintViolation()
                ok = false
            }
            hanke.endDate == null -> {
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("endDate").addConstraintViolation()
                ok = false
            }

            hanke.phase!! > 7 -> { //TODO: real phase validation checks when we know what we are passing through
                context.buildConstraintViolationWithTemplate(HankeError.HAI1002.toString()).addPropertyNode("phase").addConstraintViolation()
                ok = false
            }
        }
        return ok
    }
}